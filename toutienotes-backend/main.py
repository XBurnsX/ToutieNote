from fastapi import FastAPI, HTTPException, UploadFile, File, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel
import sqlite3, os, shutil, hashlib, uuid, json
from datetime import datetime
from pathlib import Path
from PIL import Image
import io

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
            key   TEXT PRIMARY KEY,
            value TEXT
        )
    """)
    db.execute("""
        CREATE TABLE IF NOT EXISTS albums (
            id         TEXT PRIMARY KEY,
            name       TEXT NOT NULL,
            cover_url  TEXT,
            created_at TEXT NOT NULL
        )
    """)
    db.execute("""
        CREATE TABLE IF NOT EXISTS photos (
            id         TEXT PRIMARY KEY,
            album_id   TEXT,
            filename   TEXT NOT NULL,
            created_at TEXT NOT NULL,
            FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE
        )
    """)
    db.commit()
    db.close()

init_db()

# ── Models ─────────────────────────────────────────────────────────────────────
class NoteIn(BaseModel):
    title:   str = ""
    content: str = ""

class PinSetup(BaseModel):
    pin: str  # 4 digits

class PinVerify(BaseModel):
    pin: str

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

# ── Helpers ────────────────────────────────────────────────────────────────────
def hash_pin(pin: str) -> str:
    return hashlib.sha256(pin.encode()).hexdigest()

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
    """Check if PIN has been set (first time setup)"""
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
    """Verify old PIN then allow reset — client sends old PIN, then calls pin-setup won't work since PIN exists.
       So this endpoint verifies old PIN and clears it."""
    stored = get_config("vault_pin")
    if stored and hash_pin(data.pin) != stored:
        raise HTTPException(401, "PIN incorrect")
    set_config("vault_pin", None)
    return {"ok": True}

# ══════════════════════════════════════════════════════════════════════════════
# VAULT — ALBUMS
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/api/vault/albums")
def list_albums():
    db = get_db()
    rows = db.execute("SELECT * FROM albums ORDER BY created_at DESC").fetchall()
    db.close()
    albums = []
    for r in rows:
        album = dict(r)
        # Count photos in this album
        db2 = get_db()
        count = db2.execute("SELECT COUNT(*) as cnt FROM photos WHERE album_id=?", (album["id"],)).fetchone()["cnt"]
        db2.close()
        album["photo_count"] = count
        albums.append(album)
    return albums

@app.post("/api/vault/albums")
def create_album(data: AlbumCreate):
    db = get_db()
    album_id = str(uuid.uuid4())
    now = datetime.utcnow().isoformat()
    db.execute("INSERT INTO albums VALUES(?,?,?,?)", (album_id, data.name, None, now))
    db.commit()
    db.close()
    return {"id": album_id, "name": data.name, "cover_url": None, "created_at": now, "photo_count": 0}

@app.delete("/api/vault/albums/{album_id}")
def delete_album(album_id: str):
    db = get_db()
    # Delete all photos in this album (files + db records)
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

@app.put("/api/vault/albums/{album_id}")
def rename_album(album_id: str, data: AlbumCreate):
    db = get_db()
    db.execute("UPDATE albums SET name=? WHERE id=?", (data.name, album_id))
    db.commit()
    db.close()
    return {"ok": True}

@app.put("/api/vault/albums/{album_id}/cover")
def set_album_cover(album_id: str, data: AlbumCoverUpdate):
    db = get_db()
    db.execute("UPDATE albums SET cover_url=? WHERE id=?", (data.photo_url, album_id))
    db.commit()
    db.close()
    return {"ok": True}

# ══════════════════════════════════════════════════════════════════════════════
# VAULT — PHOTOS
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/api/vault/photos")
def list_photos(album_id: str = None):
    """List all photos, optionally filtered by album_id"""
    db = get_db()
    if album_id:
        rows = db.execute("SELECT * FROM photos WHERE album_id=? ORDER BY created_at DESC", (album_id,)).fetchall()
    else:
        rows = db.execute("SELECT * FROM photos ORDER BY created_at DESC").fetchall()
    db.close()

    photos = []
    for r in rows:
        photo = dict(r)
        path = VAULT_DIR / photo["filename"]
        if path.exists():
            photo["url"] = f"/api/vault/photo/{photo['filename']}"
            photo["size"] = path.stat().st_size
            photos.append(photo)
    return photos

@app.post("/api/vault/upload")
async def upload_photo(file: UploadFile = File(...), album_id: str = None):
    ext = Path(file.filename).suffix.lower()
    if ext not in [".jpg", ".jpeg", ".png", ".webp"]:
        raise HTTPException(400, "Format non supporté")
    photo_id = str(uuid.uuid4())
    filename = f"{photo_id}{ext}"
    dest = VAULT_DIR / filename
    with open(dest, "wb") as f:
        shutil.copyfileobj(file.file, f)

    # Save to DB
    db = get_db()
    now = datetime.utcnow().isoformat()
    db.execute("INSERT INTO photos VALUES(?,?,?,?)", (photo_id, album_id, filename, now))
    db.commit()

    # Update album cover if this is the first photo
    if album_id:
        count = db.execute("SELECT COUNT(*) as cnt FROM photos WHERE album_id=?", (album_id,)).fetchone()["cnt"]
        if count == 1:  # First photo
            db.execute("UPDATE albums SET cover_url=? WHERE id=?", (f"/api/vault/photo/{filename}", album_id))
            db.commit()

    db.close()
    return {
        "id": photo_id,
        "filename": filename,
        "url": f"/api/vault/photo/{filename}",
        "album_id": album_id,
        "created_at": now
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
    cropped.save(path)  # remplace l'original dans le vault
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
    album_id = row["album_id"]
    created_at = row["created_at"]

    old_path = VAULT_DIR / old_filename
    if old_path.exists():
        old_path.unlink()

    ext = Path(file.filename).suffix.lower() or ".jpg"
    new_filename = f"{photo_id}_{int(datetime.utcnow().timestamp())}{ext}"
    new_path = VAULT_DIR / new_filename
    with open(new_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    db.execute("UPDATE photos SET filename=? WHERE id=?", (new_filename, photo_id))

    # Update album cover if it pointed to the old file
    if album_id:
        old_url = f"/api/vault/photo/{old_filename}"
        album_row = db.execute("SELECT cover_url FROM albums WHERE id=?", (album_id,)).fetchone()
        if album_row and album_row["cover_url"] == old_url:
            db.execute("UPDATE albums SET cover_url=? WHERE id=?",
                       (f"/api/vault/photo/{new_filename}", album_id))

    db.commit()
    db.close()
    return {
        "id": photo_id,
        "filename": new_filename,
        "url": f"/api/vault/photo/{new_filename}",
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

@app.delete("/api/vault/photo/{filename}")
def delete_photo(filename: str):
    # Delete from filesystem
    path = VAULT_DIR / filename
    if path.exists():
        path.unlink()
    # Delete from DB
    db = get_db()
    db.execute("DELETE FROM photos WHERE filename=?", (filename,))
    db.commit()
    db.close()
    return {"ok": True}

# ── Static files ───────────────────────────────────────────────────────────────
app.mount("/static", StaticFiles(directory="/app/static"), name="static")

@app.get("/")
def root():
    return FileResponse("/app/static/index.html")
