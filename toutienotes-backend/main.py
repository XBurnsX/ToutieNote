from fastapi import FastAPI, HTTPException, UploadFile, File, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel
import sqlite3, os, shutil, hashlib, uuid, json, threading
from datetime import datetime
from pathlib import Path
from PIL import Image
try:
    import imagehash
except ImportError:
    imagehash = None
try:
    import numpy as np
except ImportError:
    np = None
import io, sys

app = FastAPI()

STATIC_DIR = Path("/app/static")
STATIC_DIR.mkdir(parents=True, exist_ok=True)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Paths ──────────────────────────────────────────────────────────────────────
DATA_DIR   = Path("/data")
DB_PATH    = DATA_DIR / "notes.db"
VAULT_DIR  = DATA_DIR / "vault"
VAULT_DIR.mkdir(parents=True, exist_ok=True)

# ── DB init ────────────────────────────────────────────────────────────────────
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    db = get_db()
    db.execute("""
        CREATE TABLE IF NOT EXISTS notes (
            id      TEXT PRIMARY KEY,
            title   TEXT,
            content TEXT,
            updated_at TEXT
        )
    """)
    db.execute("""
        CREATE TABLE IF NOT EXISTS vault_config (
            user_id TEXT NOT NULL,
            key     TEXT NOT NULL,
            value   TEXT,
            PRIMARY KEY (user_id, key)
        )
    """)
    db.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id            TEXT PRIMARY KEY,
            username      TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            token         TEXT,
            created_at    TEXT NOT NULL
        )
    """)
    db.execute("""
        CREATE TABLE IF NOT EXISTS albums (
            id         TEXT PRIMARY KEY,
            name       TEXT NOT NULL,
            cover_url  TEXT,
            created_at TEXT NOT NULL,
            pin_hash   TEXT,
            sort_order INTEGER DEFAULT 0
        )
    """)
    db.execute("""
        CREATE TABLE IF NOT EXISTS photos (
            id                 TEXT PRIMARY KEY,
            album_id           TEXT,
            filename           TEXT NOT NULL,
            thumbnail_filename TEXT,
            media_type         TEXT DEFAULT 'image',
            phash              TEXT,
            sort_order         INTEGER DEFAULT 0,
            created_at         TEXT NOT NULL,
            FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE
        )
    """)
    migrations = [
        ("albums", "pin_hash", "TEXT"),
        ("albums", "sort_order", "INTEGER DEFAULT 0"),
        ("photos", "thumbnail_filename", "TEXT"),
        ("photos", "media_type", "TEXT DEFAULT 'image'"),
        ("photos", "phash", "TEXT"),
        ("photos", "sort_order", "INTEGER DEFAULT 0"),
        ("photos", "favorite", "INTEGER DEFAULT 0"),
    ]
    for table, col, col_type in migrations:
        try:
            db.execute(f"ALTER TABLE {table} ADD COLUMN {col} {col_type}")
        except Exception:
            pass

    db.execute("UPDATE photos SET favorite=0 WHERE favorite IS NULL")
    db.commit()
    db.close()

init_db()

# ── Models ─────────────────────────────────────────────────────────────────────
class NoteIn(BaseModel):
    title:   str = ""
    content: str = ""

class PinSetup(BaseModel):
    pin: str

class PinVerify(BaseModel):
    pin: str

class PinChange(BaseModel):
    old_pin: str
    new_pin: str

class AuthRegister(BaseModel):
    username: str
    password: str

class AuthLogin(BaseModel):
    username: str
    password: str

class CropParams(BaseModel):
    x: int
    y: int
    width: int
    height: int

class AlbumCreate(BaseModel):
    name: str

class PhotoMoveToAlbum(BaseModel):
    album_id: str

class AlbumCoverUpdate(BaseModel):
    photo_url: str

class AlbumLock(BaseModel):
    pin: str

class AlbumReorder(BaseModel):
    album_ids: list

class PhotoReorder(BaseModel):
    photo_ids: list

# ── Helpers ────────────────────────────────────────────────────────────────────
def hash_pin(pin: str) -> str:
    return hashlib.sha256(pin.encode()).hexdigest()

def compute_phash(image_path: Path) -> str:
    if imagehash is None:
        return ""
    try:
        img = Image.open(image_path)
        return str(imagehash.phash(img))
    except Exception:
        return ""

def compute_crop_hash(image_path: Path):
    if imagehash is None:
        return None
    try:
        img = Image.open(image_path)
        return imagehash.crop_resistant_hash(img, hash_func=imagehash.phash)
    except Exception:
        return None

def are_crop_similar(ch1, ch2) -> bool:
    """Check if two images are crops of each other. 30% segments, diff≤10 — détecte les vrais crops (ex. 40%)."""
    try:
        segs1 = ch1.segment_hashes
        segs2 = ch2.segment_hashes
    except Exception:
        return False
    if not segs1 or not segs2:
        return False

    small, big = (segs1, segs2) if len(segs1) <= len(segs2) else (segs2, segs1)

    matched = 0
    used = set()
    for s in small:
        best_diff = 999
        best_j = -1
        for j, b in enumerate(big):
            if j in used:
                continue
            try:
                d = s - b
                if d < best_diff:
                    best_diff = d
                    best_j = j
            except Exception:
                pass
        if best_diff <= 10 and best_j >= 0:
            matched += 1
            used.add(best_j)

    needed = max(1, int(len(small) * 0.50))
    return matched >= needed

def compute_resize_hash(image_path: Path):
    if imagehash is None:
        return None
    try:
        img = Image.open(image_path).resize((128, 128)).convert("L")
        return imagehash.phash(img, hash_size=16)
    except Exception:
        return None

# ── CLIP model (lazy-load, ~350MB first download) ─────────────────────────────
_clip_model = None

def get_clip_model():
    global _clip_model
    if _clip_model is None:
        try:
            from sentence_transformers import SentenceTransformer
            _log("Loading CLIP model (first time downloads ~350MB)...")
            _clip_model = SentenceTransformer("clip-ViT-B-32")
            _log("CLIP model loaded ✓")
        except ImportError:
            _log("sentence-transformers not installed, CLIP disabled")
            return None
    return _clip_model

def compute_clip_embedding(image_path: Path):
    model = get_clip_model()
    if model is None:
        return None
    try:
        img = Image.open(image_path).convert("RGB")
        return model.encode(img, normalize_embeddings=True)
    except Exception as e:
        _log(f"CLIP embedding failed for {image_path.name}: {e}")
        return None

def clip_cosine_sim(a, b) -> float:
    if np is None:
        return 0.0
    return float(np.dot(a, b))

# ── Other helpers ──────────────────────────────────────────────────────────────
def is_duplicate(new_phash: str, threshold: int = 5):
    if not new_phash or imagehash is None:
        return None
    db = get_db()
    rows = db.execute("SELECT id, phash FROM photos WHERE phash IS NOT NULL AND media_type='image'").fetchall()
    db.close()
    for row in rows:
        existing_hash = row["phash"]
        if existing_hash:
            try:
                diff = imagehash.hex_to_hash(new_phash) - imagehash.hex_to_hash(existing_hash)
                if diff <= threshold:
                    return dict(row)
            except Exception:
                pass
    return None

def get_config(key: str):
    db = get_db()
    row = db.execute("SELECT value FROM vault_config WHERE key=?", (key,)).fetchone()
    db.close()
    return row["value"] if row else None

def set_config(key: str, value: str):
    db = get_db()
    db.execute("INSERT OR REPLACE INTO vault_config(key,value) VALUES(?,?)", (key, value))
    db.commit()
    db.close()

# ══════════════════════════════════════════════════════════════════════════════
# NOTES
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/api/notes")
def list_notes():
    db = get_db()
    rows = db.execute("SELECT * FROM notes ORDER BY updated_at DESC").fetchall()
    db.close()
    return [dict(r) for r in rows]

@app.post("/api/notes")
def create_note(note: NoteIn):
    db = get_db()
    nid = str(uuid.uuid4())
    now = datetime.utcnow().isoformat()
    db.execute("INSERT INTO notes VALUES(?,?,?,?)", (nid, note.title, note.content, now))
    db.commit()
    db.close()
    return {"id": nid, "title": note.title, "content": note.content, "updated_at": now}

@app.put("/api/notes/{note_id}")
def update_note(note_id: str, note: NoteIn):
    db = get_db()
    now = datetime.utcnow().isoformat()
    db.execute("UPDATE notes SET title=?, content=?, updated_at=? WHERE id=?",
               (note.title, note.content, now, note_id))
    db.commit()
    db.close()
    return {"id": note_id, "updated_at": now}

@app.delete("/api/notes/{note_id}")
def delete_note(note_id: str):
    db = get_db()
    db.execute("DELETE FROM notes WHERE id=?", (note_id,))
    db.commit()
    db.close()
    return {"ok": True}

# ══════════════════════════════════════════════════════════════════════════════
# VAULT — PIN
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/api/vault/pin-exists")
def pin_exists():
    return {"exists": get_config("vault_pin") is not None}

@app.post("/api/vault/pin-setup")
def setup_pin(data: PinSetup):
    if len(data.pin) != 4 or not data.pin.isdigit():
        raise HTTPException(400, "PIN doit être 4 chiffres")
    if get_config("vault_pin") is not None:
        raise HTTPException(400, "PIN déjà configuré")
    set_config("vault_pin", hash_pin(data.pin))
    return {"ok": True}

@app.post("/api/vault/verify")
def verify_pin(data: PinVerify):
    stored = get_config("vault_pin")
    if stored is None:
        raise HTTPException(400, "PIN pas encore configuré")
    if hash_pin(data.pin) != stored:
        raise HTTPException(401, "PIN incorrect")
    return {"ok": True}

@app.post("/api/vault/reset-pin")
def reset_pin(data: PinVerify):
    stored = get_config("vault_pin")
    if stored and hash_pin(data.pin) != stored:
        raise HTTPException(401, "PIN incorrect")
    set_config("vault_pin", None)
    return {"ok": True}

@app.post("/api/vault/change-pin")
def change_pin(data: PinChange):
    stored = get_config("vault_pin")
    if stored is None:
        raise HTTPException(400, "PIN pas encore configuré")
    if hash_pin(data.old_pin) != stored:
        raise HTTPException(401, "PIN actuel incorrect")
    if len(data.new_pin) != 4 or not data.new_pin.isdigit():
        raise HTTPException(400, "Nouveau PIN doit être 4 chiffres")
    set_config("vault_pin", hash_pin(data.new_pin))
    return {"ok": True}

# ══════════════════════════════════════════════════════════════════════════════
# VAULT — ALBUMS
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/api/vault/albums")
def list_albums():
    db = get_db()
    rows = db.execute("SELECT * FROM albums ORDER BY sort_order ASC, created_at DESC").fetchall()
    db.close()
    albums = []
    for r in rows:
        album = dict(r)
        db2 = get_db()
        count = db2.execute("SELECT COUNT(*) as cnt FROM photos WHERE album_id=?", (album["id"],)).fetchone()["cnt"]
        db2.close()
        album["photo_count"] = count
        album["is_locked"] = bool(album.get("pin_hash"))
        album.pop("pin_hash", None)
        albums.append(album)
    return albums

@app.post("/api/vault/albums")
def create_album(data: AlbumCreate):
    db = get_db()
    album_id = str(uuid.uuid4())
    now = datetime.utcnow().isoformat()
    db.execute("INSERT INTO albums (id, name, cover_url, created_at) VALUES(?,?,?,?)",
               (album_id, data.name, None, now))
    db.commit()
    db.close()
    return {"id": album_id, "name": data.name, "cover_url": None, "created_at": now, "photo_count": 0, "is_locked": False}

@app.post("/api/vault/albums/{album_id}/lock")
def lock_album(album_id: str, data: AlbumLock):
    db = get_db()
    hashed = hash_pin(data.pin)
    db.execute("UPDATE albums SET pin_hash=? WHERE id=?", (hashed, album_id))
    db.commit()
    db.close()
    return {"ok": True}

@app.post("/api/vault/albums/{album_id}/verify-lock")
def verify_album_lock(album_id: str, data: AlbumLock):
    db = get_db()
    row = db.execute("SELECT pin_hash FROM albums WHERE id=?", (album_id,)).fetchone()
    db.close()
    if not row or not row["pin_hash"]:
        return {"ok": True}
    if hash_pin(data.pin) != row["pin_hash"]:
        raise HTTPException(401, "PIN incorrect pour cet album")
    return {"ok": True}

@app.delete("/api/vault/albums/{album_id}")
def delete_album(album_id: str):
    db = get_db()
    photos = db.execute("SELECT filename FROM photos WHERE album_id=?", (album_id,)).fetchall()
    for p in photos:
        path = VAULT_DIR / p["filename"]
        if path.exists():
            path.unlink()
    db.execute("DELETE FROM photos WHERE album_id=?", (album_id,))
    db.execute("DELETE FROM albums WHERE id=?", (album_id,))
    db.commit()
    db.close()
    return {"ok": True}

@app.put("/api/vault/albums/reorder")
def reorder_albums(data: AlbumReorder):
    db = get_db()
    for i, aid in enumerate(data.album_ids):
        db.execute("UPDATE albums SET sort_order=? WHERE id=?", (i, aid))
    db.commit()
    db.close()
    return {"ok": True}

@app.put("/api/vault/albums/{album_id}")
def rename_album(album_id: str, data: AlbumCreate):
    db = get_db()
    db.execute("UPDATE albums SET name=? WHERE id=?", (data.name, album_id))
    db.commit()
    db.close()
    return {"ok": True}

@app.post("/api/vault/albums/{album_id}/unlock")
def unlock_album(album_id: str, data: AlbumLock):
    db = get_db()
    row = db.execute("SELECT pin_hash FROM albums WHERE id=?", (album_id,)).fetchone()
    if row and row["pin_hash"] and hash_pin(data.pin) != row["pin_hash"]:
        db.close()
        raise HTTPException(401, "PIN incorrect")
    db.execute("UPDATE albums SET pin_hash=NULL WHERE id=?", (album_id,))
    db.commit()
    db.close()
    return {"ok": True}

@app.put("/api/vault/albums/{album_id}/cover")
def update_album_cover(album_id: str, data: AlbumCoverUpdate):
    db = get_db()
    db.execute("UPDATE albums SET cover_url=? WHERE id=?", (data.photo_url, album_id))
    db.commit()
    db.close()
    return {"ok": True}

# ══════════════════════════════════════════════════════════════════════════════
# VAULT — PHOTOS
# ══════════════════════════════════════════════════════════════════════════════

@app.put("/api/vault/albums/{album_id}/photos/reorder")
def reorder_photos(album_id: str, data: PhotoReorder):
    db = get_db()
    for i, pid in enumerate(data.photo_ids):
        db.execute("UPDATE photos SET sort_order=? WHERE id=? AND album_id=?", (i, pid, album_id))
    db.commit()
    db.close()
    return {"ok": True}

@app.get("/api/vault/photos")
def list_photos(album_id: str = None):
    db = get_db()
    if album_id:
        rows = db.execute("SELECT * FROM photos WHERE album_id=? ORDER BY COALESCE(favorite,0) DESC, sort_order ASC, created_at DESC", (album_id,)).fetchall()
    else:
        rows = db.execute("SELECT * FROM photos ORDER BY COALESCE(favorite,0) DESC, sort_order ASC, created_at DESC").fetchall()
    db.close()

    photos = []
    for r in rows:
        photo = dict(r)
        path = VAULT_DIR / photo["filename"]
        if path.exists():
            photo["url"] = f"/api/vault/photo/{photo['filename']}"
            thumb = photo.get("thumbnail_filename")
            if thumb:
                photo["thumbnail_url"] = f"/api/vault/photo/{thumb}"
            else:
                photo["thumbnail_url"] = photo["url"]
            photo["size"] = path.stat().st_size
            photos.append(photo)
    return photos

@app.post("/api/vault/upload")
async def upload_photo(file: UploadFile = File(...), album_id: str = None):
    ext = Path(file.filename).suffix.lower()
    is_video = ext in [".mp4", ".mov", ".mkv"]
    if ext not in [".jpg", ".jpeg", ".png", ".webp"] and not is_video:
        raise HTTPException(400, "Format non supporté")

    photo_id = str(uuid.uuid4())
    filename = f"{photo_id}{ext}"
    dest = VAULT_DIR / filename

    with open(dest, "wb") as f:
        shutil.copyfileobj(file.file, f)

    media_type = 'video' if is_video else 'image'
    thumbnail_filename = None
    phash_val = None

    if not is_video:
        try:
            img = Image.open(dest)
            phash_val = compute_phash(dest)
            img.thumbnail((500, 500))
            thumbnail_filename = f"thumb_{photo_id}.webp"
            thumb_dest = VAULT_DIR / thumbnail_filename
            img.save(thumb_dest, format="WEBP", quality=80)
        except Exception as e:
            print(f"Erreur traitement image: {e}")

    duplicate_of = None
    if phash_val:
        dup = is_duplicate(phash_val)
        if dup:
            duplicate_of = dup["id"]

    db = get_db()
    now = datetime.utcnow().isoformat()
    db.execute("""
        INSERT INTO photos (id, album_id, filename, thumbnail_filename, media_type, phash, created_at)
        VALUES(?,?,?,?,?,?,?)
    """, (photo_id, album_id, filename, thumbnail_filename, media_type, phash_val, now))
    db.commit()

    if album_id:
        count = db.execute("SELECT COUNT(*) as cnt FROM photos WHERE album_id=?", (album_id,)).fetchone()["cnt"]
        if count == 1:
            db.execute("UPDATE albums SET cover_url=? WHERE id=?", (f"/api/vault/photo/{filename}", album_id))
            db.commit()

    db.close()
    return {
        "id": photo_id,
        "filename": filename,
        "url": f"/api/vault/photo/{filename}",
        "thumbnail_url": f"/api/vault/photo/{thumbnail_filename}" if thumbnail_filename else f"/api/vault/photo/{filename}",
        "album_id": album_id,
        "media_type": media_type,
        "created_at": now,
        "duplicate_of": duplicate_of
    }

@app.get("/api/vault/photo/{filename}")
def get_photo(filename: str):
    path = VAULT_DIR / filename
    if not path.exists():
        raise HTTPException(404, "Photo introuvable")
    return FileResponse(path)

@app.post("/api/vault/crop/{filename}")
def crop_photo(filename: str, params: CropParams):
    path = VAULT_DIR / filename
    if not path.exists():
        raise HTTPException(404, "Photo introuvable")
    img = Image.open(path)
    cropped = img.crop((params.x, params.y, params.x + params.width, params.y + params.height))
    cropped.save(path)
    return {"ok": True, "url": f"/api/vault/photo/{filename}"}

@app.post("/api/vault/resize/{filename}")
def resize_photo(filename: str, width: int, height: int):
    path = VAULT_DIR / filename
    if not path.exists():
        raise HTTPException(404, "Photo introuvable")
    img = Image.open(path)
    resized = img.resize((width, height), Image.LANCZOS)
    resized.save(path)
    return {"ok": True, "url": f"/api/vault/photo/{filename}"}

@app.put("/api/vault/photo/{photo_id}/replace")
async def replace_photo(photo_id: str, file: UploadFile = File(...)):
    db = get_db()
    row = db.execute("SELECT * FROM photos WHERE id=?", (photo_id,)).fetchone()
    if not row:
        db.close()
        raise HTTPException(404, "Photo introuvable")

    old_filename = row["filename"]
    old_thumb = dict(row).get("thumbnail_filename")
    album_id = row["album_id"]
    created_at = row["created_at"]

    old_path = VAULT_DIR / old_filename
    if old_path.exists():
        old_path.unlink()
    if old_thumb:
        old_thumb_path = VAULT_DIR / old_thumb
        if old_thumb_path.exists():
            old_thumb_path.unlink()

    ext = Path(file.filename).suffix.lower() or ".jpg"
    new_filename = f"{photo_id}_{int(datetime.utcnow().timestamp())}{ext}"
    new_path = VAULT_DIR / new_filename
    with open(new_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    thumbnail_filename = None
    phash_val = None
    try:
        img = Image.open(new_path)
        phash_val = str(imagehash.phash(img)) if imagehash else None
        img.thumbnail((500, 500))
        thumbnail_filename = f"thumb_{photo_id}_{int(datetime.utcnow().timestamp())}.webp"
        thumb_dest = VAULT_DIR / thumbnail_filename
        img.save(thumb_dest, format="WEBP", quality=80)
    except Exception:
        pass

    db.execute(
        "UPDATE photos SET filename=?, thumbnail_filename=?, phash=? WHERE id=?",
        (new_filename, thumbnail_filename, phash_val, photo_id)
    )

    if album_id:
        old_url = f"/api/vault/photo/{old_filename}"
        album_row = db.execute("SELECT cover_url FROM albums WHERE id=?", (album_id,)).fetchone()
        if album_row and album_row["cover_url"] == old_url:
            db.execute("UPDATE albums SET cover_url=? WHERE id=?",
                       (f"/api/vault/photo/{new_filename}", album_id))

    db.commit()
    db.close()

    thumb_url = f"/api/vault/photo/{thumbnail_filename}" if thumbnail_filename else f"/api/vault/photo/{new_filename}"
    return {
        "id": photo_id,
        "filename": new_filename,
        "url": f"/api/vault/photo/{new_filename}",
        "thumbnail_url": thumb_url,
        "album_id": album_id,
        "created_at": created_at
    }

@app.put("/api/vault/photo/{photo_id}/move")
def move_photo_to_album(photo_id: str, data: PhotoMoveToAlbum):
    db = get_db()
    db.execute("UPDATE photos SET album_id=? WHERE id=?", (data.album_id, photo_id))
    db.commit()
    db.close()
    return {"ok": True}

@app.put("/api/vault/photo/{photo_id}/favorite")
def toggle_favorite(photo_id: str):
    db = get_db()
    row = db.execute("SELECT favorite FROM photos WHERE id=?", (photo_id,)).fetchone()
    if not row:
        db.close()
        raise HTTPException(404, "Photo not found")
    new_val = 0 if (dict(row).get("favorite") or 0) else 1
    db.execute("UPDATE photos SET favorite=? WHERE id=?", (new_val, photo_id))
    db.commit()
    db.close()
    return {"ok": True, "favorite": new_val == 1}

@app.delete("/api/vault/photo/{filename}")
def delete_photo(filename: str):
    path = VAULT_DIR / filename
    if path.exists():
        path.unlink()
    db = get_db()
    # Also delete thumbnail
    row = db.execute("SELECT thumbnail_filename FROM photos WHERE filename=?", (filename,)).fetchone()
    if row and row["thumbnail_filename"]:
        thumb_path = VAULT_DIR / row["thumbnail_filename"]
        if thumb_path.exists():
            thumb_path.unlink()
    db.execute("DELETE FROM photos WHERE filename=?", (filename,))
    db.commit()
    db.close()
    return {"ok": True}

# ══════════════════════════════════════════════════════════════════════════════
# VAULT — DUPLICATE SCAN (4-tier: pHash + crop_resistant + resize + CLIP)
# ══════════════════════════════════════════════════════════════════════════════

scan_progress = {}

def _log(msg):
    print(f"[Doublon] {msg}", flush=True)
    sys.stdout.flush()

def _run_scan(job_id: str, album_id):
    """
    3-tier duplicate scan (CLIP désactivé — regroupait des photos sémantiquement
    similaires, pas des vrais doublons, ex. 68 photos du même événement):
      1. pHash exact     (threshold ≤ 5)   → identical/near-identical
      2. crop_resistant  (30% segments, diff≤10) → crops légers à moyens (ex. 40%)
      3. resize pHash    (threshold ≤ 10) → resizes (était 18, trop permissif)
    """
    try:
        _log("A: _run_scan démarré")
        db = get_db()
        if album_id:
            rows = db.execute("SELECT * FROM photos WHERE album_id=? AND media_type='image'", (album_id,)).fetchall()
        else:
            rows = db.execute("SELECT * FROM photos WHERE media_type='image'").fetchall()
        db.close()
        photos = [dict(r) for r in rows]
        total = len(photos)
        scan_progress[job_id]["total"] = total
        _log(f"B: total={total} photos, imagehash={'OK' if imagehash else 'NON'}")

        if total < 2:
            scan_progress[job_id].update(done=True, groups=[], scanned=total, percent=100)
            return

        # ── Phase 1: Compute all hashes (0-30%) ─────────────────────────────
        _log("C: calcul des hash (phash + crop + resize)...")
        crop_cache = {}
        resize_cache = {}

        for i, p in enumerate(photos):
            path = VAULT_DIR / p["filename"]
            if path.exists():
                if imagehash:
                    ch = compute_crop_hash(path)
                    if ch is not None:
                        crop_cache[p["id"]] = ch
                    rh = compute_resize_hash(path)
                    if rh is not None:
                        resize_cache[p["id"]] = rh
                # CLIP désactivé: regroupait des photos sémantiquement similaires (ex. 68 photos du même événement)

            scan_progress[job_id]["scanned"] = i + 1
            scan_progress[job_id]["percent"] = min(30, int((i + 1) / total * 30))

        _log(f"D: crop_cache={len(crop_cache)} resize_cache={len(resize_cache)}")

        # ── Phase 2: Compare all pairs (30-100%) ────────────────────────────
        total_pairs = total * (total - 1) // 2 if total > 1 else 0
        _log(f"E: comparaison de {total_pairs} paires...")
        pairs_done = 0
        used = set()
        groups = []
        MAX_GROUP_SIZE = 12  # sécurité: évite les méga-groupes (ex. 68 photos) en cas de seuil trop permissif

        for i, p1 in enumerate(photos):
            if p1["id"] in used:
                continue
            group = [p1]
            used.add(p1["id"])

            for j in range(i + 1, len(photos)):
                if len(group) >= MAX_GROUP_SIZE:
                    break
                p2 = photos[j]
                if p2["id"] in used:
                    continue

                similar = False
                match_reason = ""

                # Tier 1: pHash exact (stored in DB)
                if not similar and p1.get("phash") and p2.get("phash"):
                    try:
                        diff = imagehash.hex_to_hash(p1["phash"]) - imagehash.hex_to_hash(p2["phash"])
                        if diff <= 5:
                            similar = True
                            match_reason = f"phash(diff={diff})"
                    except Exception:
                        pass

                # Tier 2: crop_resistant_hash (segment matching)
                if not similar and p1["id"] in crop_cache and p2["id"] in crop_cache:
                    if are_crop_similar(crop_cache[p1["id"]], crop_cache[p2["id"]]):
                        similar = True
                        match_reason = "crop_resistant"

                # Tier 3: resize hash
                if not similar and p1["id"] in resize_cache and p2["id"] in resize_cache:
                    try:
                        diff = resize_cache[p1["id"]] - resize_cache[p2["id"]]
                        if diff <= 10:
                            similar = True
                            match_reason = f"resize(diff={diff})"
                    except Exception:
                        pass

                if similar:
                    group.append(p2)
                    used.add(p2["id"])
                    if len(group) == 2:
                        _log(f"  MATCH {match_reason}: {p1.get('filename')} ~ {p2.get('filename')}")

                pairs_done += 1
                if pairs_done % 500 == 0:
                    pct = 30 + int(pairs_done / total_pairs * 70) if total_pairs else 100
                    scan_progress[job_id]["percent"] = min(99, pct)

            if len(group) > 1:
                for g in group:
                    g["url"] = f"/api/vault/photo/{g['filename']}"
                    thumb = g.get("thumbnail_filename")
                    g["thumbnail_url"] = f"/api/vault/photo/{thumb}" if thumb else g["url"]
                groups.append(group)

        _log(f"F: terminé. groupes={len(groups)} (total photos en doublon={sum(len(g) for g in groups)})")
        scan_progress[job_id].update(done=True, groups=groups, scanned=total, percent=100)
    except Exception as e:
        _log(f"Z: ERREUR {e}")
        import traceback
        _log(traceback.format_exc())
        scan_progress[job_id]["error"] = str(e)
        scan_progress[job_id]["done"] = True

@app.post("/api/vault/scan-duplicates")
def start_scan(album_id: str = None):
    db = get_db()
    if album_id:
        total = db.execute("SELECT COUNT(*) FROM photos WHERE album_id=? AND media_type='image'", (album_id,)).fetchone()[0]
    else:
        total = db.execute("SELECT COUNT(*) FROM photos WHERE media_type='image'").fetchone()[0]
    db.close()
    job_id = str(uuid.uuid4())
    scan_progress[job_id] = {"scanned": 0, "total": total, "percent": 0, "done": False, "groups": [], "error": None}
    threading.Thread(target=_run_scan, args=(job_id, album_id), daemon=True).start()
    return {"job_id": job_id, "total": total}

@app.get("/api/vault/scan-duplicates/status")
def scan_status(job_id: str):
    if job_id not in scan_progress:
        raise HTTPException(404, "Job not found")
    return scan_progress[job_id]

@app.get("/api/vault/photo-count")
def photo_count(album_id: str = None):
    db = get_db()
    if album_id:
        n = db.execute("SELECT COUNT(*) FROM photos WHERE album_id=? AND media_type='image'", (album_id,)).fetchone()[0]
    else:
        n = db.execute("SELECT COUNT(*) FROM photos WHERE media_type='image'").fetchone()[0]
    db.close()
    return {"count": n}

@app.post("/api/vault/scan-duplicates-sync")
def scan_duplicates_sync(album_id: str = None):
    """Version synchrone: une seule requête, retourne le résultat complet."""
    _log("A: scan-duplicates-sync appelé")
    db = get_db()
    if album_id:
        total = db.execute("SELECT COUNT(*) FROM photos WHERE album_id=? AND media_type='image'", (album_id,)).fetchone()[0]
    else:
        total = db.execute("SELECT COUNT(*) FROM photos WHERE media_type='image'").fetchone()[0]
    db.close()
    scan_progress["_sync"] = {"scanned": 0, "total": total, "percent": 0, "done": False, "groups": [], "error": None}
    _run_scan("_sync", album_id)
    s = scan_progress.pop("_sync", {})
    return {"groups": s.get("groups", []), "scanned": s.get("scanned", 0)}

# ── Static files ───────────────────────────────────────────────────────────────
app.mount("/static", StaticFiles(directory="/app/static"), name="static")

@app.get("/")
def root():
    return FileResponse("/app/static/index.html")
