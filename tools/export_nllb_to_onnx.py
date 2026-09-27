#!/usr/bin/env python
"""Export Meta NLLB-200 distilled 600M to ONNX (+ dynamic INT8) in one run.

Usage (PowerShell, from repo root):
    pip install "optimum[onnxruntime]" transformers torch onnx onnxruntime
    python tools/export_nllb_to_onnx.py --quantize int8

What it does:
  1. optimum-cli export (seq2seq-lm)  facebook/nllb-200-distilled-600M -> --out_dir
     Produces encoder_model.onnx, decoder_model.onnx (+ decoder_with_past, config, tokenizer).
  2. Optional dynamic INT8 quant of encoder+decoder
     -> encoder_model_int8.onnx, decoder_model_int8.onnx
  3. Verifies each ONNX loads in onnxruntime (unless --skip_verify).
  4. Prints next steps (host file, paste URL in Kelola Model, or adb push).

Defaults:
  --model_id facebook/nllb-200-distilled-600M
  --out_dir  models/nllb-200-distilled-600M-onnx
  --opset    14

NOTE: ~350-400MB output. Do NOT commit to git / assets. Host it and set
ModelDownloader.NLLB_URL, or `adb push` to filesDir/models/. The app also
accepts split files named <base>_encoder.onnx / <base>_decoder.onnx
(see NllbTranslator.ensureSessions).
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def run(cmd: list[str]) -> None:
    print(f"[run] {' '.join(cmd)}", flush=True)
    try:
        subprocess.run(cmd, check=True)
    except FileNotFoundError:
        sys.exit(f"Command not found: {cmd[0]}. Install requirements first.")
    except subprocess.CalledProcessError as e:
        sys.exit(f"Failed: {e}")


def export_onnx(model_id: str, out_dir: Path, opset: int, task: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    run([
        "optimum-cli", "export", "onnx",
        "--model", model_id,
        "--task", task,
        "--opset", str(opset),
        str(out_dir),
    ])


def quantize_dynamic(src: Path, dst: Path) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic
    from onnxruntime.quantization.quantize import QuantizationMode
    print(f"[quant] {src.name} -> {dst.name}", flush=True)
    quantize_dynamic(
        model_input=str(src),
        model_output=str(dst),
        weight_type=QuantType.QInt8,
        extra_options={"WeightQuantizationMode": QuantizationMode.IntegerOps},
    )


def verify_onnx(path: Path) -> None:
    import onnxruntime as ort
    sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    print(f"[verify] OK {path.name}: inputs={[i.name for i in sess.get_inputs()][:4]}")


def main() -> int:
    ap = argparse.ArgumentParser(description="NLLB-200 600M -> ONNX one-shot exporter")
    ap.add_argument("--model_id", default="facebook/nllb-200-distilled-600M")
    ap.add_argument("--out_dir", type=Path, default=REPO_ROOT / "models/nllb-200-distilled-600M-onnx")
    ap.add_argument("--opset", type=int, default=14)
    ap.add_argument("--task", default="seq2seq-lm")
    ap.add_argument("--quantize", choices=["int8", "none"], default="int8")
    ap.add_argument("--skip_verify", action="store_true")
    ap.add_argument("--skip_export", action="store_true",
                    help="Skip optimum export (only quantize/verify existing files)")
    args = ap.parse_args()

    if not args.skip_export:
        export_onnx(args.model_id, args.out_dir, args.opset, args.task)

    candidates = ["encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx"]
    found = [args.out_dir / n for n in candidates if (args.out_dir / n).is_file()]
    if not found:
        sys.exit(f"No ONNX found in {args.out_dir}. Export may have failed.")

    final_files: list[Path] = []
    if args.quantize == "int8":
        for src in found:
            # Skip past-model quant (large, rarely used on-device); keep original.
            if "with_past" in src.name:
                final_files.append(src)
                continue
            dst = src.with_name(src.stem + "_int8.onnx")
            if dst.is_file():
                print(f"[quant] reuse existing {dst.name}")
            else:
                try:
                    quantize_dynamic(src, dst)
                except ImportError:
                    sys.exit("onnxruntime not installed. Run: pip install onnxruntime")
            final_files.append(dst)
    else:
        final_files = found

    if not args.skip_verify:
        for f in final_files:
            try:
                verify_onnx(f)
            except ImportError:
                print("[verify] skip (onnxruntime not installed)")
                break
            except Exception as e:
                print(f"[verify] FAILED {f.name}: {e}")
                return 1

    print("\n==== Result ====")
    for f in sorted(args.out_dir.glob("*.onnx")):
        print(f"  {f.name}  {f.stat().st_size / 1e6:.1f} MB")
    print(
        "\nNext:\n"
        "  1. Host encoder/decoder INT8 (or single merged file as\n"
        "     nllb-200-distilled-600M-int8.onnx) on your server/Drive.\n"
        "  2. App > Kelola Model > paste URL > Download, or:\n"
        "     adb push <file> /data/data/com.example.bismillah/files/models/\n"
        "  3. For split files use names <base>_encoder.onnx / <base>_decoder.onnx."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
