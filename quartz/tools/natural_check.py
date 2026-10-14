#!/usr/bin/env python3
"""Is it natural? (plan §13.3)

Compares a camera JPEG with a neutral render of the RAW (DNG) taken in the
same shot (Quartz's RAW + JPEG format), and reports signs of processing:

  sharpening      overshoot (halo) at a high-contrast edge
  noise reduction JPEG noise far below the RAW's on flat patches
  tone curve      mid-tone response vs the plain sRGB curve
  saturation      colour saturation vs the neutral RAW render

Shoot a scene with a slanted dark/light edge, a flat grey card and some
colour (a ColorChecker is ideal), in even light, then:

    pip install rawpy numpy opencv-python-headless
    python3 tools/natural_check.py QTZ_..._.jpg QTZ_....RAW.dng [--edge x,y,w,h] [--flat x,y,w,h]

Regions are in JPEG pixel coordinates; without them the script picks the
strongest edge and the flattest patch it can find. Run it on the phone's stock
camera too: that is the "processed" baseline.
"""
import argparse
import sys

try:
    import cv2
    import numpy as np
    import rawpy
except ImportError:
    sys.exit("needs: pip install rawpy numpy opencv-python-headless")


def srgb_encode(x):
    x = np.clip(x, 0, 1)
    return np.where(x <= 0.0031308, 12.92 * x, 1.055 * np.power(x, 1 / 2.4) - 0.055)


def neutral_render(dng_path, shape):
    """Demosaic + camera white balance + sRGB curve, nothing else."""
    with rawpy.imread(dng_path) as raw:
        rgb = raw.postprocess(
            use_camera_wb=True,
            no_auto_bright=True,
            output_bps=16,
            gamma=(1, 1),
            user_flip=0,
            demosaic_algorithm=rawpy.DemosaicAlgorithm.AHD,
        ).astype(np.float64) / 65535.0
    rgb = srgb_encode(rgb)
    out = cv2.resize(rgb, (shape[1], shape[0]), interpolation=cv2.INTER_AREA)
    return out


def region(arg):
    if not arg:
        return None
    x, y, w, h = (int(v) for v in arg.split(","))
    return slice(y, y + h), slice(x, x + w)


def find_flat(gray, size=64):
    best, where = None, None
    for y in range(0, gray.shape[0] - size, size):
        for x in range(0, gray.shape[1] - size, size):
            patch = gray[y:y + size, x:x + size]
            m = patch.mean()
            if not 0.25 < m < 0.75:
                continue
            s = patch.std()
            if best is None or s < best:
                best, where = s, (slice(y, y + size), slice(x, x + size))
    return where


def find_edge(gray, size=96):
    grad = cv2.Sobel(gray, cv2.CV_64F, 1, 0) ** 2 + cv2.Sobel(gray, cv2.CV_64F, 0, 1) ** 2
    y, x = np.unravel_index(np.argmax(cv2.blur(grad, (size, size))), grad.shape)
    y0, x0 = max(0, y - size // 2), max(0, x - size // 2)
    return slice(y0, y0 + size), slice(x0, x0 + size)


def overshoot(gray):
    """Edge profile overshoot beyond the two plateau levels, as a fraction of the step."""
    gx = cv2.Sobel(gray, cv2.CV_64F, 1, 0)
    gy = cv2.Sobel(gray, cv2.CV_64F, 0, 1)
    horizontal = np.abs(gx).sum() > np.abs(gy).sum()
    profile = gray.mean(axis=0 if horizontal else 1)
    lo, hi = np.percentile(profile, 10), np.percentile(profile, 90)
    step = max(hi - lo, 1e-6)
    return max(profile.max() - hi, lo - profile.min()) / step


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("jpeg")
    ap.add_argument("dng")
    ap.add_argument("--edge")
    ap.add_argument("--flat")
    a = ap.parse_args()

    jpg = cv2.cvtColor(cv2.imread(a.jpeg, cv2.IMREAD_COLOR), cv2.COLOR_BGR2RGB).astype(np.float64) / 255.0
    ref = neutral_render(a.dng, jpg.shape)
    gj = cv2.cvtColor(jpg.astype(np.float32), cv2.COLOR_RGB2GRAY).astype(np.float64)
    gr = cv2.cvtColor(ref.astype(np.float32), cv2.COLOR_RGB2GRAY).astype(np.float64)

    edge = region(a.edge) or find_edge(gj)
    flat = region(a.flat) or find_flat(gr)
    findings = []

    o_j, o_r = overshoot(gj[edge]), overshoot(gr[edge])
    findings.append(("Sharpening (edge overshoot)", f"JPEG {o_j:.1%} vs RAW {o_r:.1%}", o_j > o_r + 0.05))

    if flat is not None:
        n_j = (gj[flat] - cv2.GaussianBlur(gj[flat], (0, 0), 3)).std()
        n_r = (gr[flat] - cv2.GaussianBlur(gr[flat], (0, 0), 3)).std()
        ratio = n_j / max(n_r, 1e-9)
        findings.append(("Noise reduction (fine noise kept)", f"{ratio:.0%} of the RAW's", ratio < 0.6))

    mid = (gr > 0.2) & (gr < 0.8)
    if mid.any():
        slope = np.polyfit(gr[mid].ravel(), gj[mid].ravel(), 1)[0]
        findings.append(("Tone curve (mid-tone contrast)", f"slope {slope:.2f} (1.00 = plain sRGB)", abs(slope - 1) > 0.15))

    hsv_j = cv2.cvtColor(jpg.astype(np.float32), cv2.COLOR_RGB2HSV)[..., 1].mean()
    hsv_r = cv2.cvtColor(ref.astype(np.float32), cv2.COLOR_RGB2HSV)[..., 1].mean()
    sat = hsv_j / max(hsv_r, 1e-9)
    findings.append(("Saturation", f"{sat:.0%} of the RAW's", sat > 1.15))

    width = max(len(f[0]) for f in findings)
    processed = False
    for name, value, flag in findings:
        processed |= flag
        print(f"{name:<{width}}  {value:<32}  {'PROCESSED' if flag else 'ok'}")
    print("\nVerdict:", "signs of processing" if processed else "natural")
    sys.exit(1 if processed else 0)


if __name__ == "__main__":
    main()
