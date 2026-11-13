"""Converts Meteocons Fill icons to Android vector drawables, gradients and clips included.

Usage, from the repository root, with the npm package `@meteocons/svg-static` 3.0.0-next.10 (MIT, Bas Milius) unpacked
outside the repository:

    python3 -I tools/meteocons/convert.py <package/fill> app/src/main/res/drawable

Covers only what these files use: groups, `clip-path` (rectangles), `path`, `circle`, `transform="translate(…)"`,
fill and stroke as a color or a linear gradient (`userSpaceOnUse`). Any other element or attribute stops the
conversion rather than being ignored.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"
NAMES = [
    "clear-day", "clear-night", "partly-cloudy-day", "partly-cloudy-night", "cloudy", "fog",
    "drizzle", "rain", "sleet", "snow", "thunderstorms",
]
HEADER = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    "<!-- Meteocons Fill \"{name}\" (basmilius/weather-icons, @meteocons/svg-static 3.0.0-next.10), "
    "Copyright (c) 2020-present Bas Milius, MIT License; converted by tools/meteocons/convert.py. -->\n"
)
PATH_ATTRS = {"id", "d", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "transform"}
CIRCLE_ATTRS = {"id", "cx", "cy", "r", "fill", "stroke"}


def fail(message):
    sys.exit(f"convert.py: {message}")


def color(value):
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", value):
        fail(f"unsupported color: {value}")
    return "#FF" + value[1:].upper()


def number(value):
    return format(float(value), "g")


class Converter:
    def __init__(self, root):
        self.gradients = {}
        self.clips = {}
        for defs in root.iter(SVG + "defs"):
            for el in defs:
                tag = el.tag[len(SVG):]
                if tag == "linearGradient":
                    if el.get("gradientUnits") != "userSpaceOnUse" or el.get("gradientTransform"):
                        fail(f"unsupported gradient: {el.get('id')}")
                    self.gradients[el.get("id")] = el
                elif tag == "clipPath":
                    shapes = list(el)
                    if len(shapes) != 1 or shapes[0].tag != SVG + "rect" or set(shapes[0].attrib) - {"width", "height", "fill", "x", "y"}:
                        fail(f"unsupported clip: {el.get('id')}")
                    r = shapes[0]
                    x, y = float(r.get("x", 0)), float(r.get("y", 0))
                    w, h = float(r.get("width")), float(r.get("height"))
                    self.clips[el.get("id")] = f"M{number(x)},{number(y)}h{number(w)}v{number(h)}h{number(-w)}z"
                else:
                    fail(f"unsupported defs element: {tag}")

    def paint(self, attr, value, indent):
        """Plain color as an attribute, or gradient as `aapt:attr` (returned separately)."""
        m = re.fullmatch(r"url\(#(.+)\)", value)
        if not m:
            return f'android:{attr}="{color(value)}"', None
        g = self.gradients.get(m.group(1))
        if g is None:
            fail(f"gradient not found: {value}")
        pad = " " * indent
        items = []
        for stop in g:
            if stop.tag != SVG + "stop" or set(stop.attrib) - {"offset", "stop-color"}:
                fail(f"unsupported gradient stop in {g.get('id')}")
            items.append(f'{pad}        <item android:offset="{number(stop.get("offset", 0))}" android:color="{color(stop.get("stop-color"))}" />')
        block = (
            f'{pad}<aapt:attr name="android:{attr}">\n'
            f'{pad}    <gradient android:type="linear" android:startX="{number(g.get("x1"))}" android:startY="{number(g.get("y1"))}"'
            f' android:endX="{number(g.get("x2"))}" android:endY="{number(g.get("y2"))}">\n'
            + "\n".join(items) + "\n"
            f"{pad}    </gradient>\n"
            f"{pad}</aapt:attr>"
        )
        return None, block

    def shape(self, el, d, indent):
        pad = " " * indent
        attrs = [f'android:pathData="{d}"']
        blocks = []
        fill = el.get("fill", "none")
        if fill != "none":
            a, b = self.paint("fillColor", fill, indent + 4)
            attrs.append(a) if a else blocks.append(b)
        stroke = el.get("stroke", "none")
        if stroke != "none":
            a, b = self.paint("strokeColor", stroke, indent + 4)
            attrs.append(a) if a else blocks.append(b)
            attrs.append(f'android:strokeWidth="{number(el.get("stroke-width", 1))}"')
            if el.get("stroke-linecap"):
                attrs.append(f'android:strokeLineCap="{el.get("stroke-linecap")}"')
            if el.get("stroke-linejoin"):
                attrs.append(f'android:strokeLineJoin="{el.get("stroke-linejoin")}"')
            attrs.append(f'android:strokeMiterLimit="{number(el.get("stroke-miterlimit", 4))}"')
        head = f"{pad}<path\n" + "\n".join(f"{pad}    {a}" for a in attrs)
        if not blocks:
            return head + " />"
        return head + ">\n" + "\n".join(blocks) + f"\n{pad}</path>"

    def element(self, el, indent):
        tag = el.tag[len(SVG):]
        pad = " " * indent
        if tag == "g":
            if set(el.attrib) - {"id", "clip-path"}:
                fail(f"unsupported group attribute: {el.attrib}")
            clip = el.get("clip-path")
            # Group without a clip: flattened, its elements keep the parent's indentation.
            if clip is None:
                return "\n".join(self.element(c, indent) for c in el)
            children = [self.element(c, indent + 4) for c in el]
            m = re.fullmatch(r"url\(#(.+)\)", clip)
            path = self.clips.get(m.group(1)) if m else None
            if path is None:
                fail(f"clip not found: {clip}")
            return f"{pad}<group>\n{pad}    <clip-path android:pathData=\"{path}\" />\n" + "\n".join(children) + f"\n{pad}</group>"
        if tag == "path":
            if set(el.attrib) - PATH_ATTRS:
                fail(f"unsupported path attribute: {set(el.attrib) - PATH_ATTRS}")
            transform = el.get("transform")
            if transform is None:
                return self.shape(el, el.get("d"), indent)
            m = re.fullmatch(r"translate\(([-\d.]+)[ ,]+([-\d.]+)\)", transform)
            if not m:
                fail(f"unsupported transform: {transform}")
            inner = self.shape(el, el.get("d"), indent + 4)
            return (
                f'{pad}<group android:translateX="{number(m.group(1))}" android:translateY="{number(m.group(2))}">\n'
                f"{inner}\n{pad}</group>"
            )
        if tag == "circle":
            if set(el.attrib) - CIRCLE_ATTRS:
                fail(f"unsupported circle attribute: {set(el.attrib) - CIRCLE_ATTRS}")
            cx, cy, r = (float(el.get(k)) for k in ("cx", "cy", "r"))
            d = f"M{number(cx - r)},{number(cy)}a{number(r)},{number(r)} 0 1,0 {number(2 * r)},0a{number(r)},{number(r)} 0 1,0 {number(-2 * r)},0z"
            return self.shape(el, d, indent)
        if tag == "defs":
            return ""
        fail(f"unsupported element: {tag}")


def convert(svg_path, name):
    root = ET.parse(svg_path).getroot()
    if root.get("viewBox") != "0 0 128 128" or root.get("fill", "none") != "none":
        fail(f"{name}: unexpected root frame or fill")
    c = Converter(root)
    body = "\n".join(part for part in (c.element(el, 4) for el in root) if part)
    return (
        HEADER.format(name=name)
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        + '    android:width="32dp"\n    android:height="32dp"\n'
        + '    android:viewportWidth="128"\n    android:viewportHeight="128">\n'
        + body + "\n</vector>\n"
    )


def main(source, out_dir):
    for name in NAMES:
        xml = convert(os.path.join(source, name + ".svg"), name)
        with open(os.path.join(out_dir, "weather_" + name.replace("-", "_") + ".xml"), "w", encoding="utf-8") as f:
            f.write(xml)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
