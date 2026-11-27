from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
BLUE = (22, 82, 246, 255)
INK = (17, 17, 17, 255)
MUTED = (101, 98, 93, 255)
BG = (241, 240, 236, 255)
SOFT_BLUE = (232, 238, 255, 255)
WHITE = (255, 255, 255, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        Path("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
    ]
    for candidate in candidates:
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def rutinitas_mark(size: int) -> Image.Image:
    """Compact open-book mark matching the Android launcher vector."""
    render_size = size * 3
    layer = Image.new("RGBA", (render_size, render_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    scale = render_size / 48
    point = lambda x, y: (round(x * scale), round(y * scale))
    def cubic(p0, p1, p2, p3, steps=24):
        points = []
        for i in range(steps + 1):
            t = i / steps
            u = 1 - t
            x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
            y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
            points.append(point(x, y))
        return points

    outline = []
    outline += cubic((7.5, 15.3), (12.5, 13), (18, 13.9), (24, 17.8))
    outline += cubic((24, 17.8), (30, 13.9), (35.5, 13), (40.5, 15.3))[1:]
    outline += [point(40.5, 34.2)]
    outline += cubic((40.5, 34.2), (34.3, 31.8), (28.5, 32.8), (24, 37.3))[1:]
    outline += cubic((24, 37.3), (19.5, 32.8), (13.7, 31.8), (7.5, 34.2))[1:]
    outline += [point(7.5, 15.3)]
    draw.polygon(outline, fill=WHITE)

    detail_stroke = max(1, round(1.45 * scale))
    details = [
        ((24, 18.2), (24, 35.4)),
        ((10.8, 20.4), (19.5, 22.8)),
        ((10.8, 25.5), (19.5, 27.9)),
        ((28.5, 22.8), (37.2, 20.4)),
        ((28.5, 27.9), (37.2, 25.5)),
    ]
    for start, end in details:
        draw.line((point(*start), point(*end)), fill=BLUE, width=detail_stroke)
    return layer.resize((size, size), Image.Resampling.LANCZOS)


def make_icon() -> None:
    image = Image.new("RGBA", (512, 512), BLUE)
    image.alpha_composite(rutinitas_mark(512))
    image.convert("RGB").save(OUT / "icon-512.png", optimize=True)


def make_feature_graphic() -> None:
    width, height = 1024, 500
    image = Image.new("RGBA", (width, height))
    pixels = image.load()
    for x in range(width):
        t = x / (width - 1)
        color = tuple(round(BG[i] * (1 - t) + SOFT_BLUE[i] * t) for i in range(3)) + (255,)
        for y in range(height):
            pixels[x, y] = color

    draw = ImageDraw.Draw(image)
    # Quiet decorative field on the right; the brand mark remains well inside safe margins.
    draw.ellipse((700, 30, 1120, 450), fill=(221, 229, 252, 255))
    mark_tile = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
    ImageDraw.Draw(mark_tile).rounded_rectangle((0, 0, 320, 320), radius=76, fill=BLUE)
    mark_tile.alpha_composite(rutinitas_mark(320))
    image.alpha_composite(mark_tile, (700, 80))

    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((78, 87, 174, 183), radius=28, fill=BLUE)
    logo = rutinitas_mark(96)
    image.alpha_composite(logo, (78, 87))
    draw = ImageDraw.Draw(image)
    draw.text((198, 83), "Rutinitas", font=font(76, bold=True), fill=INK)
    draw.text((82, 239), "Jadwal ibadah & rutinitas harian", font=font(34, bold=True), fill=INK)
    draw.text((84, 303), "Hafalan terarah. Jadwal salat offline.", font=font(23), fill=MUTED)
    draw.text((84, 339), "Data tetap di perangkat.", font=font(23), fill=MUTED)
    draw.text((84, 410), "JADWAL IBADAH   ·   HAFALAN   ·   KEBIASAAN", font=font(17, bold=True), fill=BLUE)
    image.convert("RGB").save(OUT / "feature-graphic-1024x500.png", optimize=True)


if __name__ == "__main__":
    make_icon()
    make_feature_graphic()
