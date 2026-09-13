"""
TRUE lip-sync engine opsional (Wav2Lip).

Aktif hanya bila SEMUA terpenuhi:
- ENABLE_WAV2LIP = 1 (atau true/yes/on)
- WAV2LIP_DIR    = path folder clone repo Wav2Lip (berisi inference.py)
- WAV2LIP_CKPT   = path checkpoint (mis. .../checkpoints/wav2lip_gan.pth)
- python (WAV2LIP_PYTHON, default "python") punya PyTorch terpasang

Bila salah satu belum siap, run_wav2lip() mengembalikan False sehingga /lipsync
otomatis fallback ke overlay suara (tetap menghasilkan video dengan suara baru).

Gunakan status() / endpoint GET /lipsync-status untuk mendiagnosis apa yang kurang.

Catatan: Wav2Lip bisa menganimasikan bibir dari VIDEO wajah maupun dari FOTO diam
(inference.py otomatis mode statis bila --face berupa gambar).
"""
import os
import subprocess


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def enabled() -> bool:
    return _env("ENABLE_WAV2LIP", "").lower() in ("1", "true", "yes", "on")


def is_ready() -> bool:
    if not enabled():
        return False
    wav2lip_dir = _env("WAV2LIP_DIR")
    ckpt = _env("WAV2LIP_CKPT")
    if not wav2lip_dir or not os.path.isdir(wav2lip_dir):
        return False
    if not ckpt or not os.path.exists(ckpt):
        return False
    return os.path.exists(os.path.join(wav2lip_dir, "inference.py"))


def _torch_check(python_bin: str) -> dict:
    try:
        out = subprocess.run(
            [python_bin, "-c",
             "import torch; print(torch.__version__); print(torch.cuda.is_available())"],
            capture_output=True, timeout=90,
        )
        if out.returncode != 0:
            return {"importOk": False, "error": out.stderr.decode(errors="ignore")[:300]}
        lines = out.stdout.decode(errors="ignore").strip().splitlines()
        version = lines[0].strip() if len(lines) > 0 else "?"
        cuda = (lines[1].strip() == "True") if len(lines) > 1 else False
        return {"importOk": True, "version": version, "cudaAvailable": cuda}
    except Exception as e:
        return {"importOk": False, "error": str(e)[:300]}


def status() -> dict:
    wav2lip_dir = _env("WAV2LIP_DIR")
    ckpt = _env("WAV2LIP_CKPT")
    python_bin = _env("WAV2LIP_PYTHON", "python")
    inference = os.path.join(wav2lip_dir, "inference.py") if wav2lip_dir else ""
    st = {
        "enabled": enabled(),
        "wav2lipDir": wav2lip_dir,
        "wav2lipDirExists": bool(wav2lip_dir) and os.path.isdir(wav2lip_dir),
        "inferenceExists": bool(inference) and os.path.exists(inference),
        "checkpoint": ckpt,
        "checkpointExists": bool(ckpt) and os.path.exists(ckpt),
        "pythonBin": python_bin,
        "extraArgs": _env("WAV2LIP_EXTRA_ARGS", ""),
        "ready": is_ready(),
    }
    st["torch"] = _torch_check(python_bin)
    # Ringkasan tindakan bila belum siap
    hints = []
    if not st["enabled"]:
        hints.append("Set ENABLE_WAV2LIP=1 SEBELUM start server (di proses yang sama).")
    if not st["wav2lipDirExists"]:
        hints.append("WAV2LIP_DIR belum menunjuk ke folder clone Wav2Lip yang valid.")
    elif not st["inferenceExists"]:
        hints.append("inference.py tidak ada di WAV2LIP_DIR.")
    if not st["checkpointExists"]:
        hints.append("WAV2LIP_CKPT (wav2lip_gan.pth) tidak ditemukan.")
    if not st["torch"].get("importOk"):
        hints.append("PyTorch tidak bisa di-import oleh pythonBin. Set WAV2LIP_PYTHON ke python venv yang ada torch.")
    elif not st["torch"].get("cudaAvailable"):
        hints.append("CUDA tidak terdeteksi -> jalan di CPU (lambat). Install torch build cu121 untuk pakai RTX 4060.")
    st["hints"] = hints
    return st


def run_wav2lip(face_path: str, audio: str, out_path: str) -> bool:
    """Jalankan Wav2Lip. face_path boleh berupa video ATAU gambar (foto diam).
    Return True bila output berhasil dibuat."""
    if not is_ready():
        print("Wav2Lip: BELUM SIAP (enabled=%s, ready=%s). Cek GET /lipsync-status."
              % (enabled(), is_ready()))
        return False
    wav2lip_dir = _env("WAV2LIP_DIR")
    ckpt = _env("WAV2LIP_CKPT")
    python_bin = _env("WAV2LIP_PYTHON", "python")
    cmd = [
        python_bin, os.path.join(wav2lip_dir, "inference.py"),
        "--checkpoint_path", ckpt,
        "--face", face_path,
        "--audio", audio,
        "--outfile", out_path,
    ]
    extra = _env("WAV2LIP_EXTRA_ARGS", "").strip()
    if extra:
        cmd += extra.split()
    print("Wav2Lip: menjalankan ->", " ".join(cmd))
    try:
        proc = subprocess.run(cmd, capture_output=True, cwd=wav2lip_dir)
    except Exception as e:
        print("Wav2Lip: gagal memanggil subprocess:", str(e)[:300])
        return False
    if proc.returncode != 0:
        print("Wav2Lip ERROR (returncode %s):" % proc.returncode,
              proc.stderr.decode(errors="ignore")[-1500:])
        return False
    if not os.path.exists(out_path):
        print("Wav2Lip: selesai tapi output tidak ditemukan:", out_path)
        return False
    print("Wav2Lip: SUKSES ->", out_path)
    return True
