"""
TRUE lip-sync engine opsional (Wav2Lip) — dioptimalkan untuk GPU 8GB (mis. RTX 4060).

Aktif hanya bila SEMUA terpenuhi:
- ENABLE_WAV2LIP = 1 (atau true/yes/on)
- WAV2LIP_DIR    = path folder clone repo Wav2Lip (berisi inference.py)
- WAV2LIP_CKPT   = path checkpoint (mis. .../checkpoints/wav2lip_gan.pth)
- python (WAV2LIP_PYTHON, default "python") punya PyTorch terpasang

Bila salah satu belum siap, run_wav2lip() mengembalikan False sehingga /lipsync
otomatis fallback ke overlay suara (tetap menghasilkan video dengan suara baru).

Gunakan status() / endpoint GET /lipsync-status untuk mendiagnosis apa yang kurang.

KONFIGURASI PERFORMA (semua ADA DEFAULT aman untuk RTX 4060 / VRAM 8GB).
Ubah lewat environment variable SEBELUM start server:
- WAV2LIP_RESIZE_FACTOR       (default 1)   downscale internal Wav2Lip. Naikkan ke 2/3 bila
                                            muncul "Image too big" / CUDA out of memory.
- WAV2LIP_FACE_DET_BATCH_SIZE (default 4)   batch deteksi wajah. Kecil = hemat VRAM.
- WAV2LIP_BATCH_SIZE          (default 64)  batch Wav2Lip. Kecil = hemat VRAM.
- WAV2LIP_PADS                (default "0 10 0 0")  padding kotak wajah (atas bawah kiri kanan).
- WAV2LIP_MAX_FACE_HEIGHT     (default 1280) tinggi maksimum frame wajah yang dikirim ke Wav2Lip
                                            (remake.py yang menerapkan). Kecil = jauh lebih hemat VRAM.
- WAV2LIP_TIMEOUT_SEC         (default 900) batas waktu 1 proses Wav2Lip (detik).
- WAV2LIP_EXTRA_ARGS          (opsional) argumen tambahan mentah; menimpa default di atas bila
                                            flag yang sama disebut.

Catatan: Wav2Lip bisa menganimasikan bibir dari VIDEO wajah maupun dari FOTO diam
(inference.py otomatis mode statis bila --face berupa gambar).

GPU di-serialkan: hanya 1 proses Wav2Lip berjalan pada satu waktu (mencegah dua job
rebutan VRAM -> CUDA out of memory). Job berikutnya otomatis mengantre.
"""
import os
import subprocess
import threading

# Kunci global GPU: cegah 2 proses Wav2Lip jalan bersamaan (penyebab utama CUDA OOM).
_GPU_LOCK = threading.Lock()


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def _env_int(name: str, default: int) -> int:
    try:
        v = str(os.environ.get(name, "")).strip()
        return int(v) if v else default
    except Exception:
        return default


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


def config() -> dict:
    """Konfigurasi performa aktif + default-nya (dipakai juga oleh /lipsync-status)."""
    return {
        "resizeFactor": _env_int("WAV2LIP_RESIZE_FACTOR", 1),
        "faceDetBatchSize": _env_int("WAV2LIP_FACE_DET_BATCH_SIZE", 4),
        "wav2lipBatchSize": _env_int("WAV2LIP_BATCH_SIZE", 64),
        "pads": _env("WAV2LIP_PADS", "0 10 0 0"),
        "maxFaceHeight": _env_int("WAV2LIP_MAX_FACE_HEIGHT", 1280),
        "timeoutSec": _env_int("WAV2LIP_TIMEOUT_SEC", 900),
        "extraArgs": _env("WAV2LIP_EXTRA_ARGS", ""),
    }


def max_face_height() -> int:
    return _env_int("WAV2LIP_MAX_FACE_HEIGHT", 1280)


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
        "config": config(),
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


def _build_perf_args(cfg: dict) -> list:
    """Susun argumen performa default, lewati flag yang sudah ada di WAV2LIP_EXTRA_ARGS."""
    extra = (cfg.get("extraArgs") or "").strip()
    present = set(tok for tok in extra.split() if tok.startswith("--"))
    args = []
    if "--resize_factor" not in present:
        args += ["--resize_factor", str(max(1, int(cfg["resizeFactor"])))]
    if "--face_det_batch_size" not in present:
        args += ["--face_det_batch_size", str(max(1, int(cfg["faceDetBatchSize"])))]
    if "--wav2lip_batch_size" not in present:
        args += ["--wav2lip_batch_size", str(max(1, int(cfg["wav2lipBatchSize"])))]
    if "--pads" not in present and str(cfg.get("pads", "")).strip():
        args += ["--pads"] + str(cfg["pads"]).split()
    if extra:
        args += extra.split()
    return args


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
    cfg = config()
    cmd = [
        python_bin, os.path.join(wav2lip_dir, "inference.py"),
        "--checkpoint_path", ckpt,
        "--face", face_path,
        "--audio", audio,
        "--outfile", out_path,
    ] + _build_perf_args(cfg)
    # Kurangi fragmentasi VRAM (mengurangi kemungkinan OOM di GPU kecil).
    child_env = os.environ.copy()
    child_env.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")
    print("Wav2Lip: menjalankan ->", " ".join(cmd))
    # Serialkan GPU: hanya 1 proses pada satu waktu.
    with _GPU_LOCK:
        try:
            proc = subprocess.run(
                cmd, capture_output=True, cwd=wav2lip_dir, env=child_env,
                timeout=max(60, int(cfg["timeoutSec"])),
            )
        except subprocess.TimeoutExpired:
            print("Wav2Lip: TIMEOUT setelah %ss (proses terlalu lama)." % cfg["timeoutSec"])
            return False
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
