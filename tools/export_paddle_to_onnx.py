#!/usr/bin/env python
"""Export PaddleOCR PP-OCRv4 mobile models (det/rec/cls) to ONNX in one run.

Usage (PowerShell, from repo root):
    pip install paddlepaddle paddle2onnx onnx onnxruntime
    python tools/export_paddle_to_onnx.py ^
        --det_dir downloads/ch_ppocr_mobile_v4_det_infer ^
        --rec_dir downloads/ch_ppocr_mobile_v4_rec_infer ^
        --cls_dir downloads/ch_ppocr_mobile_v4_cls_infer ^
        --dict_src downloads/ppocr_keys_v1.txt

What it does:
  1. Finds *.pdmodel + *.pdiparams in each --*_dir.
  2. Runs `paddle2onnx` for each -> <out_dir>/ch_ppocr_mobile_{det,rec,cls}.onnx
  3. Copies dict -> assets/dict/ppocr_keys_v1.txt
  4. Verifies each ONNX with onnxruntime (unless --skip_verify).

Defaults match the Android app:
  --out_dir  app/src/main/assets/paddle
  --dict_out app/src/main/assets/dict
Get the Paddle inference models + dict from the official PaddleOCR repo
(PP-OCRv4 mobile docs) before running this script.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

MODELS = (
    ("det", "ch_ppocr_mobile_det.onnx"),
    ("rec", "ch_ppocr_mobile_rec.onnx"),
    ("cls", "ch_ppocr_mobile_cls.onnx"),
)


def find_paddle_files(model_dir: Path) -> tuple[Path, Path]:
    # Format lama: *.pdmodel | Format baru Paddle 3.x PIR: *.json
    pdmodels = sorted(model_dir.glob("*.pdmodel")) + sorted(model_dir.glob("*.json"))
    pdiparams = sorted(model_dir.glob("*.pdiparams"))
    if not pdmodels or not pdiparams:
        raise FileNotFoundError(
            f"{model_dir} must contain (*.pdmodel atau *.json) + *.pdiparams "
            f"(found: {[p.name for p in model_dir.iterdir()]})"
        )
    return pdmodels[0], pdiparams[0]


def run_paddle2onnx(model_dir: Path, out_file: Path, opset: int) -> None:
    pdmodel, pdiparams = find_paddle_files(model_dir)
    out_file.parent.mkdir(parents=True, exist_ok=True)
    base_args = [
        "--model_dir", str(model_dir),
        "--model_filename", pdmodel.name,
        "--params_filename", pdiparams.name,
        "--save_file", str(out_file),
        "--opset_version", str(opset),
        "--enable_onnx_checker", "True",
    ]
    # paddle2onnx 1.x: CLI `paddle2onnx ...`; 2.x: `python -m paddle2onnx ...`
    candidates = [shutil.which("paddle2onnx")]
    prefix = Path(sys.prefix)
    candidates += [
        str(prefix / "Scripts" / "paddle2onnx.exe"),
        str(prefix / "bin" / "paddle2onnx"),
    ]
    exe = next((c for c in candidates if c and Path(c).is_file()), None)
    if exe is not None:
        cmd = [exe] + base_args
    else:
        cmd = [sys.executable, "-m", "paddle2onnx"] + base_args
    print(f"[paddle2onnx] {' '.join(cmd)}", flush=True)
    try:
        subprocess.run(cmd, check=True)
    except FileNotFoundError:
        sys.exit("paddle2onnx not found. Install: pip install paddle2onnx")
    except subprocess.CalledProcessError as e:
        sys.exit(f"paddle2onnx failed for {model_dir}: {e}")
    # paddle2onnx 1.x menulis file kosong bila model .json PIR tidak didukung.
    try:
        if out_file.is_file() and out_file.stat().st_size < 1024:
            out_file.unlink()
            sys.exit(
                f"Convert {model_dir.name} gagal (output kosong). "
                "Model .json Anda format PIR Paddle 3.x — unduh varian legacy "
                "'ch_PP-OCRv4_*_infer' yang berisi inference.pdmodel, lalu ulangi."
            )
    except SystemExit:
        raise
    except Exception:
        pass


def verify_onnx(path: Path) -> None:
    try:
        import onnxruntime as ort
    except ImportError:
        print(f"[verify] skip {path.name} (onnxruntime not installed)")
        return
    sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    print(f"[verify] OK {path.name}: inputs={[i.name for i in sess.get_inputs()]}")


def main() -> int:
    ap = argparse.ArgumentParser(description="PaddleOCR -> ONNX one-shot exporter")
    ap.add_argument("--det_dir", type=Path, required=True, help="Paddle infer dir for detector")
    ap.add_argument("--rec_dir", type=Path, required=True, help="Paddle infer dir for recognizer")
    ap.add_argument("--cls_dir", type=Path, default=None, help="Paddle infer dir for classifier (opsional, 0/180 deg)")
    ap.add_argument("--dict_src", type=Path, default=None, help="Path to ppocr_keys_v1.txt")
    ap.add_argument("--out_dir", type=Path, default=REPO_ROOT / "app/src/main/assets/paddle")
    ap.add_argument("--dict_out", type=Path, default=REPO_ROOT / "app/src/main/assets/dict")
    ap.add_argument("--opset", type=int, default=11)
    ap.add_argument("--skip_verify", action="store_true")
    ap.add_argument("--only_dict", action="store_true",
                    help="Hanya copy dict, lewati convert ONNX (untuk ONNX manual/komunitas)")
    args = ap.parse_args()

    if args.only_dict:
        if args.dict_src is None or not args.dict_src.is_file():
            sys.exit("--dict_src tidak valid untuk --only_dict")
        args.dict_out.mkdir(parents=True, exist_ok=True)
        dst = args.dict_out / "ppocr_keys_v1.txt"
        shutil.copyfile(args.dict_src, dst)
        print(f"[done] dict -> {dst}")
        return 0

    dirs = {"det": args.det_dir, "rec": args.rec_dir, "cls": args.cls_dir}
    for key, onnx_name in MODELS:
        d = dirs[key]
        if d is None:
            print(f"[skip] {key} (tidak disediakan --{key}_dir, pakai fallback rasio H/W)")
            continue
        if not d.is_dir():
            sys.exit(f"--{key}_dir not found: {d}")
        out = args.out_dir / onnx_name
        run_paddle2onnx(d, out, args.opset)
        print(f"[done] {out} ({out.stat().st_size / 1e6:.1f} MB)")
        if not args.skip_verify:
            verify_onnx(out)

    if args.dict_src is not None:
        if not args.dict_src.is_file():
            sys.exit(f"--dict_src not found: {args.dict_src}")
        args.dict_out.mkdir(parents=True, exist_ok=True)
        dst = args.dict_out / "ppocr_keys_v1.txt"
        shutil.copyfile(args.dict_src, dst)
        print(f"[done] dict -> {dst}")

    print("\nNext: rebuild the app. PaddleOcrEngine loads filesDir/models/paddle/ "
          "with fallback to assets/paddle/ (see PaddleOcrEngine.copyAssetIfNeeded).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
