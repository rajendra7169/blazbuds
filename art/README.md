# Device pictures

Each supported device is our own 3D model, built in Blender from scratch with product photos used only as a shape
reference. Nothing here is a manufacturer's image.

Render the Liberty 4 NC (Blender 4.2, Cycles on the processor, about a minute each):

```sh
blender -b -P art/models/liberty4nc.py -- out 720 112 0.35 liberty4nc_open 1
blender -b -P art/models/liberty4nc.py -- out 720 0 0 liberty4nc_closed 0
```

Arguments: output folder, size, lid angle, how far the earbuds rise, file name, earbuds shown (1) or not (0).
The app uses them as WebP at quality 86 in `ui/src/commonMain/composeResources/drawable/`.
