"""Our own 3D model of the Liberty 4 NC case and earbuds, built from scratch with the product photo only as a shape
reference. Renders transparent PNGs: blender -b -P liberty4nc.py -- <out_dir> <size> [lid_angle_deg] [rise]"""
import bpy, math, sys
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "/tmp/l4nc"
SIZE = int(argv[1]) if len(argv) > 1 else 800
LID = float(argv[2]) if len(argv) > 2 else 112.0
RISE = float(argv[3]) if len(argv) > 3 else 1.0
NAME = argv[4] if len(argv) > 4 else "liberty4nc"
SHOW_BUDS = (argv[5] if len(argv) > 5 else "1") == "1"

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

def material(name, color, metallic=0.0, rough=0.3, coat=0.0, emit=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Metallic"].default_value = metallic
    p.inputs["Roughness"].default_value = rough
    p.inputs["Coat Weight"].default_value = coat
    p.inputs["Coat Roughness"].default_value = 0.08
    if emit:
        p.inputs["Emission Color"].default_value = (*emit, 1)
        p.inputs["Emission Strength"].default_value = 6.0
    return m

def srgb(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c)

NAVY = material("navy", srgb("22305e"), metallic=0.55, rough=0.32, coat=0.9)
NAVY_IN = material("navy_inside", srgb("1a2650"), metallic=0.3, rough=0.45, coat=0.4)
TIP = material("tip", srgb("8d939c"), rough=0.55)
LED = material("led", (1, 1, 1), emit=(1, 1, 1))
MESH = material("mesh", srgb("101420"), rough=0.6)

def rounded(name, size, at, bevel, mat, subdiv=2, segments=6):
    bpy.ops.mesh.primitive_cube_add(location=at)
    o = bpy.context.object
    o.name = name
    o.scale = (size[0] / 2, size[1] / 2, size[2] / 2)
    bpy.ops.object.transform_apply(scale=True)
    b = o.modifiers.new("bevel", "BEVEL")
    b.width = bevel
    b.segments = segments
    b.limit_method = "NONE"
    s = o.modifiers.new("smooth", "SUBSURF")
    s.levels = subdiv
    s.render_levels = subdiv
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o

def cut(target, tool):
    m = target.modifiers.new("cut", "BOOLEAN")
    m.object = tool
    m.operation = "DIFFERENCE"
    # Booleans go before the smoothing so the edges stay soft.
    while target.modifiers.find("cut") > 0:
        bpy.ops.object.select_all(action="DESELECT")
        target.select_set(True)
        bpy.context.view_layer.objects.active = target
        bpy.ops.object.modifier_move_up(modifier="cut")
    tool.hide_render = True
    tool.hide_viewport = True

def apply_all(o):
    bpy.ops.object.select_all(action="DESELECT")
    o.select_set(True)
    bpy.context.view_layer.objects.active = o
    for m in list(o.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)

# The case: one rounded pebble, split at the seam into the part that holds the earbuds and the lid.
RIM = 3.6
TOP = 5.9
def pebble(name, mat):
    # Rounded whole first, then cut, so the two halves meet as one smooth stone with only a thin seam.
    o = rounded(name, (6.2, 3.9, TOP), (0, 0, TOP / 2), 1.85, mat, segments=10)
    apply_all(o)
    return o

base = pebble("base", NAVY)
cut(base, rounded("above", (9, 7, 6), (0, 0, RIM + 3.0 + 0.02), 0.01, NAVY_IN, subdiv=0))
cut(base, rounded("cavity", (5.2, 2.9, 3.4), (0, 0, RIM + 0.6), 1.0, NAVY_IN))
apply_all(base)
# The floor and walls inside, a little darker.
inner = rounded("inner", (5.0, 2.3, 1.2), (0, 0, RIM - 2.3), 0.5, NAVY_IN)

lid = pebble("lid", NAVY)
cut(lid, rounded("below", (9, 7, 6), (0, 0, RIM - 3.0 + 0.02), 0.01, NAVY, subdiv=0))
cut(lid, rounded("hollow", (5.6, 3.3, 4.6), (0, 0, TOP / 2 - 0.35), 1.45, NAVY_IN))
apply_all(lid)
# The lid swings up and back about the hinge along the back of the seam.
hinge = Vector((0, 1.85, RIM))
bpy.ops.object.empty_add(location=hinge)
pivot = bpy.context.object
lid.parent = pivot
lid.matrix_parent_inverse = pivot.matrix_world.inverted()
pivot.rotation_euler = (math.radians(-LID), 0, 0)

# The light bar on the front.
bpy.ops.mesh.primitive_cube_add(location=(0, -1.90, RIM - 1.35))
led = bpy.context.object
led.scale = (0.55, 0.12, 0.09)
led.data.materials.append(LED)
bev = led.modifiers.new("b", "BEVEL"); bev.width = 0.07; bev.segments = 4

def bud(name, at, tilt, turn, mirror):
    """A Liberty 4 NC earbud as it sits in the case: the flat paddle stem upright, the round body and its tip at the
    top, behind the stem, pointing back."""
    parts = []
    stem = rounded(name + "_stem", (0.92, 0.5, 3.1), (0, 0, 0), 0.28, NAVY, segments=6)
    parts.append(stem)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.82, location=(0.05, 0.6, 0.75), segments=64, ring_count=32)
    head = bpy.context.object; head.scale = (0.95, 1.0, 0.9); head.data.materials.append(NAVY); bpy.ops.object.shade_smooth()
    parts.append(head)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.46, location=(0.3, 1.25, 0.82), segments=48, ring_count=24)
    tip = bpy.context.object; tip.scale = (0.9, 0.8, 1); tip.data.materials.append(TIP); bpy.ops.object.shade_smooth()
    parts.append(tip)
    # The microphone slit near the foot of the stem, as in the photo.
    bpy.ops.mesh.primitive_cube_add(location=(0, -0.255, -1.05))
    mic = bpy.context.object; mic.scale = (0.16, 0.03, 0.08); mic.data.materials.append(MESH)
    mb = mic.modifiers.new("b", "BEVEL"); mb.width = 0.06; mb.segments = 3
    parts.append(mic)
    bpy.ops.object.empty_add(location=(0, 0, 0))
    root = bpy.context.object; root.name = name
    for p in parts:
        p.parent = root
    root.location = at
    root.rotation_euler = (math.radians(tilt), 0, math.radians(turn))
    if mirror:
        root.scale = (-1, 1, 1)
    return root

rise = RISE * 1.7
if SHOW_BUDS:
  bud("left", (-1.2, -0.35, RIM + 0.35 + rise), -6, 42, False)
  bud("right", (1.25, 0.1, RIM + 0.05 + rise * 0.85), -4, -38, True)


# A soft studio: big key light, fill and a rim behind, over a neutral world.
def light(name, at, energy, size, color=(1, 1, 1)):
    bpy.ops.object.light_add(type="AREA", location=at)
    l = bpy.context.object
    l.data.energy = energy; l.data.size = size; l.data.color = color
    d = Vector((0, 0, 2.5)) - l.location
    l.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    return l

light("key", (-4, -6, 15), 1900, 9)
light("fill", (8, -6, 4), 700, 8, (0.85, 0.9, 1))
light("rim", (2, 9, 8), 1300, 7)
world = bpy.data.worlds.new("w"); scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.35, 0.37, 0.42, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.6

bpy.ops.object.camera_add(location=(7.5, -15.5, 12.5))
cam = bpy.context.object
cam.data.lens = 60
target = Vector((0.0, 0.6, 3.9))
cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 96
scene.cycles.use_denoising = True
scene.render.film_transparent = True
scene.render.resolution_x = SIZE
scene.render.resolution_y = SIZE
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "AgX"
scene.render.filepath = f"{OUT}/{NAME}.png"
bpy.ops.render.render(write_still=True)
