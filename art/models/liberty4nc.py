"""Liberty 4 NC, second build: our own model, from the product photos as shape reference only, no logos.
blender -b -P liberty4nc_v2.py -- <out_dir> <size> <lid_deg> <rise 0..1> <name> <turn_deg> <samples>"""
import bpy, math, sys
from mathutils import Vector

a = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = a[0] if a else "/tmp/l4"
SIZE = int(a[1]) if len(a) > 1 else 700
LID = float(a[2]) if len(a) > 2 else 105.0
RISE = float(a[3]) if len(a) > 3 else 0.5
NAME = a[4] if len(a) > 4 else "l4nc"
TURN = float(a[5]) if len(a) > 5 else 0.0
SAMPLES = int(a[6]) if len(a) > 6 else 128

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

def srgb(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c)

def mat(name, hexcolor, metal=0.0, rough=0.3, coat=0.0, emit=0.0, aniso=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = (*srgb(hexcolor), 1)
    p.inputs["Metallic"].default_value = metal
    p.inputs["Roughness"].default_value = rough
    p.inputs["Coat Weight"].default_value = coat
    p.inputs["Coat Roughness"].default_value = 0.06
    p.inputs["Anisotropic"].default_value = aniso
    if emit:
        p.inputs["Emission Color"].default_value = (1, 1, 1, 1)
        p.inputs["Emission Strength"].default_value = emit
    return m

CASE = mat("case", "2d3f78", metal=0.55, rough=0.32, coat=0.6)
CASE_IN = mat("case_in", "1b2756", metal=0.25, rough=0.14, coat=0.5)
FACE = mat("stem_face", "4a5f9f", metal=0.75, rough=0.34, aniso=0.6)
BODY = mat("body", "22316b", metal=0.35, rough=0.12, coat=0.9)
TIP = mat("tip", "7a808b", rough=0.6)
BLACK = mat("black", "0a0c11", rough=0.45)
GOLD = mat("gold", "d6ad52", metal=1.0, rough=0.22)
LED = mat("led", "ffffff", emit=9.0)

def select_only(o):
    bpy.ops.object.select_all(action="DESELECT"); o.select_set(True); bpy.context.view_layer.objects.active = o

def box(name, size, at, bevel, material, seg=8, sub=2):
    bpy.ops.mesh.primitive_cube_add(location=at)
    o = bpy.context.object; o.name = name
    o.scale = (size[0] / 2, size[1] / 2, size[2] / 2)
    bpy.ops.object.transform_apply(scale=True)
    if bevel > 0:
        b = o.modifiers.new("bevel", "BEVEL"); b.width = bevel; b.segments = seg; b.limit_method = "NONE"
    if sub:
        s = o.modifiers.new("sub", "SUBSURF"); s.levels = sub; s.render_levels = sub
    o.data.materials.append(material)
    bpy.ops.object.shade_smooth()
    return o

def bake(o):
    select_only(o)
    for m in list(o.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)

def carve(o, tool, op="DIFFERENCE"):
    bake(tool)
    m = o.modifiers.new("carve", "BOOLEAN"); m.object = tool; m.operation = op; m.solver = "EXACT"
    bake(o)
    bpy.data.objects.remove(tool)

def sphere(name, r, at, scale, material):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=at, segments=64, ring_count=32)
    o = bpy.context.object; o.name = name; o.scale = scale
    o.data.materials.append(material); bpy.ops.object.shade_smooth()
    return o

# ---- The case: a soft rounded cube, a little narrower at the top, split where the lid meets the base. ----
W, D, H = 6.0, 3.4, 5.6
SEAM = 2.95
WALL = 0.2

def shell():
    o = box("shell", (W, D, H), (0, 0, H / 2), 1.65, CASE, seg=12)
    t = o.modifiers.new("taper", "SIMPLE_DEFORM"); t.deform_method = "TAPER"; t.factor = -0.1; t.deform_axis = "Z"
    c = o.modifiers.new("soften", "CAST"); c.factor = 0.12; c.cast_type = "SPHERE"
    bake(o)
    return o

import bmesh

def hollow(o, cap_z, keep_above):
    """Removes the flat cap left by the cut at the seam, then gives the open shell its wall, darker inside."""
    me = o.data
    bm = bmesh.new(); bm.from_mesh(me)
    caps = [f for f in bm.faces if all(abs(v.co.z - cap_z) < 1e-3 for v in f.verts)]
    bmesh.ops.delete(bm, geom=caps, context="FACES")
    bm.to_mesh(me); bm.free()
    sol = o.modifiers.new("wall", "SOLIDIFY"); sol.thickness = WALL; sol.offset = -1
    sol.use_even_offset = True; sol.material_offset = 1; sol.material_offset_rim = 1
    o.data.materials.append(CASE_IN)
    bake(o)
    select_only(o); bpy.ops.object.shade_smooth()

base = shell(); base.name = "base"
carve(base, box("above", (10, 10, 6), (0, 0, SEAM + 3), 0, CASE, sub=0))
hollow(base, SEAM, False)
# The insert the earbuds sit in, darker and glossy, with a well for each earbud.
insert = box("insert", (W - 1.1, D - 1.0, 1.4), (0, 0, SEAM - 1.25), 0.7, CASE_IN, seg=8)
bake(insert)
for x in (-1.25, 1.25):
    carve(insert, box("well", (1.15, 1.6, 3), (x, 0.15, SEAM - 0.2), 0.5, CASE_IN, seg=6))
# Gold contacts at the bottom of each well.
for x in (-1.25, 1.25):
    for dz in (0, 0.28):
        bpy.ops.mesh.primitive_cylinder_add(radius=0.055, depth=0.06, location=(x - 0.42, 0.35, SEAM - 1.4 + dz), rotation=(0, math.radians(90), 0))
        bpy.context.object.data.materials.append(GOLD)

lid = shell(); lid.name = "lid"
carve(lid, box("below", (10, 10, 6), (0, 0, SEAM - 3), 0, CASE, sub=0))
hollow(lid, SEAM, True)
hinge = Vector((0, D / 2 - 0.15, SEAM))
bpy.ops.object.empty_add(location=hinge)
pivot = bpy.context.object
lid.parent = pivot; lid.matrix_parent_inverse = pivot.matrix_world.inverted()
pivot.rotation_euler = (math.radians(-LID), 0, 0)

# The light: a pill-shaped tab on the front, just under the seam, with a glowing bar in it.
tab = box("tab", (1.8, 0.5, 0.62), (0, -D / 2 + 0.12, SEAM - 0.34), 0.28, CASE, seg=8)
bar = box("bar", (1.25, 0.12, 0.13), (0, -D / 2 - 0.13, SEAM - 0.34), 0.06, LED, seg=4, sub=1)
# The USB-C port at the foot of the front.
port = box("port", (0.62, 0.3, 0.16), (0, -D / 2 + 0.38, 0.42), 0.08, BLACK, seg=4, sub=1)

def paddle(name, half_w, half_d, length, at, material):
    """A capsule with an oval cross-section: an oval tube with a rounded end at each side, like the earbud's stem."""
    end = half_w * 0.95
    bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=1, depth=length - 2 * end, location=at)
    tube = bpy.context.object; tube.scale = (half_w, half_d, 1)
    pieces = [tube]
    for sign in (1, -1):
        bpy.ops.mesh.primitive_uv_sphere_add(radius=1, segments=48, ring_count=24, location=(at[0], at[1], at[2] + sign * (length / 2 - end)))
        cap = bpy.context.object; cap.scale = (half_w, half_d, end); pieces.append(cap)
    bpy.ops.object.select_all(action="DESELECT")
    for piece in pieces:
        piece.select_set(True)
    bpy.context.view_layer.objects.active = tube
    bpy.ops.object.join()
    o = bpy.context.object; o.name = name
    bpy.ops.object.transform_apply(scale=True)
    t = o.modifiers.new("taper", "SIMPLE_DEFORM"); t.deform_method = "TAPER"; t.factor = 0.08; t.deform_axis = "Z"
    o.data.materials.append(material)
    bpy.ops.object.shade_smooth()
    return o

# ---- An earbud: a flat paddle stem with a satin face, the glossy body and grey tip at its top, behind. ----
def earbud(name, at, tilt, turn, mirror):
    parts = []
    stem = paddle(name + "_stem", 0.47, 0.27, 3.0, (0, 0, 0), BODY)
    parts.append(stem)
    face = paddle(name + "_face", 0.37, 0.05, 2.6, (0, -0.23, 0.08), FACE)
    parts.append(face)
    parts.append(box(name + "_mic", (0.32, 0.05, 0.16), (0, -0.27, -1.2), 0.07, BLACK, seg=4, sub=1))
    body = sphere(name + "_body", 0.7, (0.06, 0.5, 1.0), (1.05, 1.0, 0.95), BODY)
    parts.append(body)
    bpy.ops.mesh.primitive_cylinder_add(radius=0.36, depth=0.6, location=(0.35, 1.15, 1.0), rotation=(math.radians(-90), 0, math.radians(-25)))
    nozzle = bpy.context.object; nozzle.data.materials.append(BODY); bpy.ops.object.shade_smooth(); parts.append(nozzle)
    tip = sphere(name + "_tip", 0.55, (0.5, 1.5, 1.0), (1.0, 0.62, 1.0), TIP)
    tip.rotation_euler = (0, 0, math.radians(-25)); parts.append(tip)
    for dz in (-0.45, -0.95):
        bpy.ops.mesh.primitive_cylinder_add(radius=0.07, depth=0.04, location=(0, 0.26, dz), rotation=(math.radians(90), 0, 0))
        bpy.context.object.data.materials.append(GOLD); parts.append(bpy.context.object)
    bpy.ops.object.empty_add(location=(0, 0, 0))
    root = bpy.context.object; root.name = name
    for p in parts:
        p.parent = root
    root.location = at
    root.rotation_euler = (math.radians(tilt), 0, math.radians(turn))
    if mirror:
        root.scale = (-1, 1, 1)
    return root

lift = RISE * 1.9
earbud("left", (-1.25, 0.05, SEAM + 0.05 + lift), -14, 22, False)
earbud("right", (1.25, 0.2, SEAM - 0.25 + lift * 0.8), -10, -22, True)

# Turn everything together for the turntable.
bpy.ops.object.empty_add(location=(0, 0, 0))
world_root = bpy.context.object
for o in list(scene.objects):
    if o.parent is None and o is not world_root and o.type in {"MESH", "EMPTY"}:
        o.parent = world_root
world_root.rotation_euler = (0, 0, math.radians(TURN))

# ---- Studio light and camera ----
def light(at, energy, size, color=(1, 1, 1)):
    bpy.ops.object.light_add(type="AREA", location=at)
    l = bpy.context.object; l.data.energy = energy; l.data.size = size; l.data.color = color
    l.rotation_euler = (Vector((0, 0, 3)) - l.location).to_track_quat("-Z", "Y").to_euler()

light((-7, -9, 12), 2400, 10)
light((9, -7, 5), 900, 10, (0.86, 0.9, 1))
light((3, 10, 9), 1800, 8)
light((0, -4, -3), 250, 8)
w = bpy.data.worlds.new("w"); scene.world = w; w.use_nodes = True
w.node_tree.nodes["Background"].inputs["Color"].default_value = (0.42, 0.44, 0.5, 1)
w.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.55

bpy.ops.object.camera_add(location=(7.0, -14.5, 11.5))
cam = bpy.context.object; cam.data.lens = 64
cam.rotation_euler = (Vector((0, 0.3, 3.4)) - cam.location).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

scene.render.engine = "CYCLES"; scene.cycles.device = "CPU"
scene.cycles.samples = SAMPLES; scene.cycles.use_denoising = True
scene.render.film_transparent = True
scene.render.resolution_x = SIZE; scene.render.resolution_y = SIZE
scene.render.image_settings.file_format = "PNG"; scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "AgX"
scene.render.filepath = f"{OUT}/{NAME}.png"
bpy.ops.render.render(write_still=True)
