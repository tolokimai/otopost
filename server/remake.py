"""
OtoPost Remake / Lipsync endpoints (modul terpisah agar main.py yang sudah jalan aman).

- POST /upload  : upload media (video/photo/audio) -> {mediaId, url}
- POST /lipsync : tempel SUARA BARU ke media (foto/video) dengan aturan durasi.

Aturan durasi (SUARA = acuan utama, tidak ada syarat video harus lebih panjang dari suara):
- Foto  -> dijadikan video sepanjang audio (efek gerak / Ken Burns via zoompan)
- Video lebih pendek dari audio -> di-loop sampai audio selesai
- Video lebih panjang dari audio -> dipotong mengikuti panjang audio

TRUE lipsync (gerak bibir mengikuti kata baru) aktif hanya bila ENABLE_WAV2LIP=1
dan Wav2Lip sudah disiapkan (lihat lipsync_engine.py). Bila tidak, otomatis fallback
ke overlay suara + aturan durasi di atas (tetap menghasilkan video jadi dengan suara baru).
"""
import os
import uuid
import glob
import shutil
import subprocess
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, UploadFile, File, Form
from pydantic import BaseModel

WORK_DIR = os.environ.get("CLIP_WORK_DIR", "/data/clips")
PUBLIC_BASE_URL = os.environ.get("PUBLIC_BASE_URL", "").rstrip("/")
CLIP_SERVER_TOKEN = os.environ.get("CLIP_SERVER_TOKEN", "")
ENABLE_WAV2LIP = os.environ.get("ENABLE_WAV2LIP", "").lower() in ("1", "true", "yes", "on")
MEDIA_DIR = os.path.join(WORK_DIR, "media")
os.makedirs(MEDIA_DIR, exist_ok=True)

router = APIRouter()

ALLOWED = {
    "video": [".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v"],
    "photo": [".jpg", ".jpeg", ".png", ".webp", ".bmp"],
    "audio": [".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus"],
}


def _check_auth(authorization: Optional[str]):
    if not CLIP_SERVER_TOKEN:
        return
    if authorization != ("Bearer " + CLIP_SERVER_TOKEN):
        raise HTTPException(status_code=401, detail="Token tidak valid")


def _file_url(rel_path: str) -> str:
    return (PUBLIC_BASE_URL if PUBLIC_BASE_URL else "") + "/files/" + rel_path


def _media_path(media_id: str) -> Optional[str]:
    if not media_id:
        return None
    cands = glob.glob(os.path.join(MEDIA_DIR, media_id + ".*"))
    return cands[0] if cands else None


def _ffprobe_duration(path: str) -> float:
    try:
        out = subprocess.check_output([
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", path,
        ]).decode().strip()
        return float(out)
    except Exception:
        return 0.0


def _target_scale(aspect: str) -> str:
    if aspect == "1:1":
        return "1080:1080"
    if aspect == "16:9":
        return "1920:1080"
    return "1080:1920"  # 9:16 default


# ---------------- Upload ----------------
@router.post("/upload")
async def upload(
    kind: str = Form(...),
    file: UploadFile = File(...),
    authorization: Optional[str] = Header(default=None),
):
    _check_auth(authorization)
    if kind not in ALLOWED:
        raise HTTPException(status_code=400, detail="kind harus video/photo/audio")
    _, ext = os.path.splitext(file.filename or "")
    ext = ext.lower()
    if ext not in ALLOWED[kind]:
        ext = ALLOWED[kind][0]
    media_id = kind + "_" + uuid.uuid4().hex[:12]
    dest = os.path.join(MEDIA_DIR, media_id + ext)
    try:
        with open(dest, "wb") as out:
            shutil.copyfileobj(file.file, out)
    finally:
        file.file.close()
    if not os.path.exists(dest) or os.path.getsize(dest) == 0:
        raise HTTPException(status_code=500, detail="Gagal menyimpan file")
    return {"mediaId": media_id, "url": _file_url("media/" + media_id + ext)}


# ---------------- Lipsync / Remake ----------------
class LipsyncRequest(BaseModel):
    mediaId: str
    mediaKind: str  # "video" | "photo"
    audioId: str
    mode: Optional[str] = "lipsync"   # "lipsync" | "overlay"
    aspectRatio: Optional[str] = "9:16"
    subtitle: Optional[bool] = False
    subtitleStyle: Optional[str] = "clean"


def _photo_to_video(photo: str, audio: str, dur: float, scale: str, out_path: str) -> bool:
    w, h = scale.split(":")
    frames = max(1, int(round(dur * 30)))
    vf = (
        "scale=" + w + ":" + h + ":force_original_aspect_ratio=increase,"
        "crop=" + w + ":" + h + ","
        "zoompan=z='min(zoom+0.0005,1.15)':d=" + str(frames) + ":s=" + w + "x" + h + ":fps=30,"
        "format=yuv420p"
    )
    cmd = [
        "ffmpeg", "-y", "-loop", "1", "-i", photo, "-i", audio,
        "-vf", vf, "-t", str(dur),
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
        "-c:a", "aac", "-b:a", "128k", "-shortest", "-movflags", "+faststart", out_path,
    ]
    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        print("WARNING: photo_to_video ffmpeg gagal:", proc.stderr.decode(errors="ignore")[:400])
    return proc.returncode == 0 and os.path.exists(out_path)


def _video_remux(video: str, audio: str, adur: float, vdur: float, scale: str, out_path: str) -> bool:
    w, h = scale.split(":")
    vf = (
        "scale=" + w + ":" + h + ":force_original_aspect_ratio=increase,"
        "crop=" + w + ":" + h + ",setsar=1,format=yuv420p"
    )
    cmd = ["ffmpeg", "-y"]
    if vdur > 0 and vdur < adur:
        # video lebih pendek dari audio -> loop sampai audio selesai
        cmd += ["-stream_loop", "-1", "-i", video]
    else:
        # video lebih panjang / tidak diketahui -> potong mengikuti audio
        cmd += ["-i", video]
    cmd += [
        "-i", audio, "-vf", vf, "-t", str(adur),
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
        "-c:a", "aac", "-b:a", "128k", "-shortest", "-movflags", "+faststart", out_path,
    ]
    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        print("WARNING: video_remux ffmpeg gagal:", proc.stderr.decode(errors="ignore")[:400])
    return proc.returncode == 0 and os.path.exists(out_path)


def _scale_to_aspect(video: str, scale: str, out_path: str) -> bool:
    """Skala + crop video ke rasio target (silent) untuk dijadikan input wajah Wav2Lip."""
    w, h = scale.split(":")
    vf = (
        "scale=" + w + ":" + h + ":force_original_aspect_ratio=increase,"
        "crop=" + w + ":" + h + ",setsar=1,format=yuv420p"
    )
    cmd = [
        "ffmpeg", "-y", "-i", video, "-an", "-vf", vf,
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", out_path,
    ]
    proc = subprocess.run(cmd, capture_output=True)
    return proc.returncode == 0 and os.path.exists(out_path)


@router.post("/lipsync")
def lipsync(req: LipsyncRequest, authorization: Optional[str] = Header(default=None)):
    _check_auth(authorization)
    media = _media_path(req.mediaId)
    audio = _media_path(req.audioId)
    if not media:
        raise HTTPException(status_code=404, detail="Media tidak ditemukan: " + str(req.mediaId))
    if not audio:
        raise HTTPException(status_code=404, detail="Audio tidak ditemukan: " + str(req.audioId))
    adur = _ffprobe_duration(audio)
    if adur <= 0:
        raise HTTPException(status_code=400, detail="Durasi audio tidak terbaca")

    scale = _target_scale(req.aspectRatio or "9:16")
    job = "rmk_" + uuid.uuid4().hex[:12]
    tmp = os.path.join(WORK_DIR, job)
    os.makedirs(tmp, exist_ok=True)
    out_name = "remake.mp4"
    out_path = os.path.join(tmp, out_name)

    lipsync_applied = False
    ok = False

    if (req.mediaKind or "video") == "photo":
        # Foto tidak punya bibir untuk digerakkan -> selalu Ken Burns + suara baru.
        ok = _photo_to_video(media, audio, adur, scale, out_path)
    else:
        vdur = _ffprobe_duration(media)
        want_lipsync = (req.mode or "lipsync") == "lipsync"
        if want_lipsync and ENABLE_WAV2LIP:
            try:
                from lipsync_engine import run_wav2lip
                face = os.path.join(tmp, "face.mp4")
                if _scale_to_aspect(media, scale, face) and run_wav2lip(face, audio, out_path):
                    lipsync_applied = True
                    ok = True
            except Exception as e:
                print("WARNING: wav2lip gagal, fallback overlay:", str(e))
        if not ok:
            ok = _video_remux(media, audio, adur, vdur, scale, out_path)

    if not ok or not os.path.exists(out_path):
        raise HTTPException(status_code=500, detail="Gagal memproses remake")

    final_dur = _ffprobe_duration(out_path)
    return {
        "job": job,
        "downloadUrl": _file_url(job + "/" + out_name),
        "durationSec": int(round(final_dur if final_dur > 0 else adur)),
        "lipsyncApplied": lipsync_applied,
    }
