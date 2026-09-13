"""
TRUE lip-sync engine opsional (Wav2Lip).

Aktif hanya bila:
- ENABLE_WAV2LIP=1
- WAV2LIP_DIR  : path folder clone repo Wav2Lip (berisi inference.py)
- WAV2LIP_CKPT : path checkpoint (mis. .../checkpoints/wav2lip_gan.pth)

Bila salah satu belum siap, run_wav2lip() mengembalikan False sehingga /lipsync
otomatis fallback ke overlay suara (tetap menghasilkan video dengan suara baru).

Catatan jujur: Wav2Lip butuh PyTorch + checkpoint (~400MB) dan CPU/GPU yang memadai.
Di CPU, klip pendek (<= 60 detik) masih wajar; klip panjang bisa lambat.
"""
import os
import subprocess


def is_ready() -> bool:
    wav2lip_dir = os.environ.get("WAV2LIP_DIR", "")
    ckpt = os.environ.get("WAV2LIP_CKPT", "")
    if not wav2lip_dir or not os.path.isdir(wav2lip_dir):
        return False
    if not ckpt or not os.path.exists(ckpt):
        return False
    return os.path.exists(os.path.join(wav2lip_dir, "inference.py"))


def run_wav2lip(face_video: str, audio: str, out_path: str) -> bool:
    if not is_ready():
        return False
    wav2lip_dir = os.environ.get("WAV2LIP_DIR", "")
    ckpt = os.environ.get("WAV2LIP_CKPT", "")
    python_bin = os.environ.get("WAV2LIP_PYTHON", "python")
    cmd = [
        python_bin, os.path.join(wav2lip_dir, "inference.py"),
        "--checkpoint_path", ckpt,
        "--face", face_video,
        "--audio", audio,
        "--outfile", out_path,
    ]
    extra = os.environ.get("WAV2LIP_EXTRA_ARGS", "").strip()
    if extra:
        cmd += extra.split()
    proc = subprocess.run(cmd, capture_output=True, cwd=wav2lip_dir)
    if proc.returncode != 0:
        print("Wav2Lip error:", proc.stderr.decode(errors="ignore")[:600])
        return False
    return os.path.exists(out_path)
