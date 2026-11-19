# Device pictures

Each supported device is our own 3D model, built in Blender from scratch with product photos used only as a shape
reference. Nothing here is a manufacturer's image.

The app shows each model as frames rendered ahead of time: the lid opening and the earbuds rising (12), and a turn
all the way round, every 30° (12). It blends neighbouring frames, so a drag or a tap moves it like live 3D, at about
230 KB a model instead of a 3D engine.

Render the Liberty 4 NC's frames (Blender 4.2, Cycles on the processor, a few seconds each):

```sh
cd art/models && ./render_frames.sh out 512
```

One picture: `blender -b -P liberty4nc.py -- <folder> <size> <lid angle> <rise 0..1> <name> <turn degrees> <samples>`.
The app takes them as WebP at quality 82, named `l4nc_open_NN` and `l4nc_turn_NN`, in
`ui/src/commonMain/composeResources/drawable/`.

## Blazbuds universal earbuds

Our own design with the Blazbuds mark printed on the lid and the stems, shown for every pair that has no model of its
own. `cd art/models && ./render_universal.sh out 512`, then name the WebP frames `gen_open_NN` and `gen_turn_NN`.
The mark is `blazbuds_logo_print.png`.
