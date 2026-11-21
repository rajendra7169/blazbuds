package com.blazify.buds.core.eq

// Made by tools/autoeq/refresh.py from AutoEq's measurements; run it again rather than editing by hand.

/** Every built-in correction: a model, the bands it was fitted to, their step, and the gains. */
internal val TUNED_TABLE: List<TunedCurve> = listOf(
    // Anker Soundcore Liberty 4 NC: Rtings/Bruel & Kjaer 5128 in-ear/Anker Soundcore Liberty 4 NC (by Rtings)
    TunedCurve("Liberty 4 NC", listOf("A3947", "Liberty 4 NC"), "anker-soundcore-liberty-4-nc", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-4.3, 2.0, 3.7, 4.0, 2.7, 4.8, -2.7, 4.5)),
    // Anker Soundcore Liberty 4: Rtings/HMS II.3 in-ear/Anker Soundcore Liberty 4 (by Rtings)
    TunedCurve("Liberty 4", listOf("A3953", "Liberty 4"), "anker-soundcore-liberty-4", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(5.1, 4.4, 6.0, 6.0, 1.1, 6.0, -5.9, -6.0)),
    // Anker Liberty 4 Pro: DHRME/in-ear/Anker Liberty 4 Pro (by DHRME)
    TunedCurve("Liberty 4 Pro", listOf("A3954", "Liberty 4 Pro"), "anker-liberty-4-pro", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-2.8, -0.8, 0.4, 1.5, 0.2, 2.3, 4.1, -0.3)),
    // Anker Liberty 5: DHRME/in-ear/Anker Liberty 5 (by DHRME)
    TunedCurve("Liberty 5", listOf("A3957", "Liberty 5"), "anker-liberty-5", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.4, 6.0, -0.6, 2.3, -0.1, 1.6, 2.2, 3.3)),
    // Anker Soundcore Liberty 3 Pro: Rtings/HMS II.3 in-ear/Anker Soundcore Liberty 3 Pro (by Rtings)
    TunedCurve("Liberty 3 Pro", listOf("A3952", "Liberty 3 Pro"), "anker-soundcore-liberty-3-pro", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(1.1, 5.3, 6.0, 6.0, 2.3, 5.3, -0.1, -6.0)),
    // Anker Soundcore Liberty 2 Pro: oratory1990/in-ear/Anker Soundcore Liberty 2 Pro (by oratory1990)
    TunedCurve("Liberty 2 Pro", listOf("A3909", "Liberty 2 Pro"), "anker-soundcore-liberty-2-pro", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.1, 2.5, 6.0, 6.0, 4.5, 2.2, 3.2, -3.3)),
    // Anker Soundcore Liberty Air 2 Pro: Rtings/HMS II.3 in-ear/Anker Soundcore Liberty Air 2 Pro (by Rtings)
    TunedCurve("Liberty Air 2 Pro", listOf("A3951", "Liberty Air 2 Pro"), "anker-soundcore-liberty-air-2-pro", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.4, 2.3, 0.7, 3.9, 2.7, 5.6, -6.0, 1.6)),
    // Anker Soundcore Life A2 NC: Rtings/HMS II.3 in-ear/Anker Soundcore Life A2 NC (by Rtings)
    TunedCurve("Life A2 NC", listOf("A3931", "Life A2 NC", "Life Dot 2 NC"), "anker-soundcore-life-a2-nc", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-4.6, 0.2, 4.3, 6.0, 3.3, 5.1, -1.7, -6.0)),
    // Anker Soundcore Life P3: Rtings/HMS II.3 in-ear/Anker Soundcore Life P3 (by Rtings)
    TunedCurve("Life P3", listOf("A3939", "Life P3"), "anker-soundcore-life-p3", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(2.1, 5.4, 1.3, 5.6, -0.1, 5.5, 3.5, -2.7)),
    // Anker Soundcore Life Q30: Rtings/Bruel & Kjaer 5128 over-ear/Anker Soundcore Life Q30 (by Rtings)
    TunedCurve("Life Q30", listOf("A3028", "Life Q30"), "anker-soundcore-life-q30", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-5.7, 1.1, 0.4, 5.3, 4.1, 2.3, 0.9, -2.2)),
    // Anker Soundcore Life Q35: Rtings/HMS II.3 over-ear/Anker Soundcore Life Q35 (by Rtings)
    TunedCurve("Life Q35", listOf("A3027", "Life Q35"), "anker-soundcore-life-q35", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.8, 5.4, 4.0, 4.6, 1.3, 6.0, 6.0, -6.0)),
    // Anker Soundcore Space A40: Rtings/Bruel & Kjaer 5128 in-ear/Anker Soundcore Space A40 (by Rtings)
    TunedCurve("Space A40", listOf("A3936", "Space A40"), "anker-soundcore-space-a40", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.4, -1.6, -1.5, -0.4, -0.5, 4.6, -3.2, 5.8)),
    // Anker Soundcore Space One: oratory1990/over-ear/Anker Soundcore Space One (by oratory1990)
    TunedCurve("Space One", listOf("A3035", "Space One"), "anker-soundcore-space-one", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.0, 3.2, 6.0, 3.9, -2.5, 6.0, 1.3, -6.0)),
    // Anker Soundcore Space Q45: Rtings/Bruel & Kjaer 5128 over-ear/Anker Soundcore Space Q45 (by Rtings)
    TunedCurve("Space Q45", listOf("A3040", "Space Q45"), "anker-soundcore-space-q45", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-3.5, 0.7, 3.5, -1.6, -4.1, 5.6, -6.0, 1.2)),
    // Anker Soundcore P40i: Rtings/Bruel & Kjaer 5128 in-ear/Anker Soundcore P40i (by Rtings)
    TunedCurve("P40i", listOf("A3955", "P40i"), "anker-soundcore-p40i", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(-6.0, 6.0, 6.0, 6.0, 1.2, 3.1, -2.3, -2.6)),
    // Anker Soundcore C30i: Rtings/Bruel & Kjaer 5128 in-ear/Anker Soundcore C30i (by Rtings)
    TunedCurve("C30i", listOf("A3330", "C30i"), "anker-soundcore-c30i", listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800), 0.1, listOf(4.3, 3.3, -2.0, -3.4, -3.0, -6.0, -6.0, 6.0)),
    // Samsung Galaxy Buds3: Rtings/Bruel & Kjaer 5128 in-ear/Samsung Galaxy Buds3 (by Rtings)
    TunedCurve("Galaxy Buds3", listOf("Galaxy Buds3"), "samsung-galaxy-buds3", listOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-1.0, -2.0, -1.0, -2.0, -1.0, -4.0, 3.0, -2.0, 4.0)),
    // Samsung Galaxy Buds3 Pro: Rtings/Bruel & Kjaer 5128 in-ear/Samsung Galaxy Buds3 Pro (by Rtings)
    TunedCurve("Galaxy Buds3 Pro", listOf("Galaxy Buds3 Pro"), "samsung-galaxy-buds3-pro", listOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-2.0, 1.0, 2.0, 5.0, 4.0, 5.0, 4.0, 2.0, 4.0)),
    // Sony WH-1000XM3: oratory1990/over-ear/Sony WH-1000XM3 (by oratory1990)
    TunedCurve("WH-1000XM3", listOf("WH-1000XM3"), "sony-wh-1000xm3", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(-1.0, 4.0, 5.0, 3.0, 2.0)),
    // Sony WH-1000XM3: oratory1990/over-ear/Sony WH-1000XM3 (by oratory1990)
    TunedCurve("WH-1000XM3", listOf("WH-1000XM3"), "sony-wh-1000xm3", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-3.0, -2.0, -5.0, 0.0, -1.0, 3.0, 5.0, 5.0, 1.0, 3.0)),
    // Sony WH-1000XM4: oratory1990/over-ear/Sony WH-1000XM4 (by oratory1990)
    TunedCurve("WH-1000XM4", listOf("WH-1000XM4"), "sony-wh-1000xm4", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(2.0, 3.0, 6.0, 2.0, 1.0)),
    // Sony WH-1000XM4: oratory1990/over-ear/Sony WH-1000XM4 (by oratory1990)
    TunedCurve("WH-1000XM4", listOf("WH-1000XM4"), "sony-wh-1000xm4", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-3.0, -1.0, -5.0, 0.0, 2.0, 1.0, 6.0, 1.0, 3.0, -1.0)),
    // Sony WH-1000XM5: oratory1990/over-ear/Sony WH-1000XM5 (by oratory1990)
    TunedCurve("WH-1000XM5", listOf("WH-1000XM5"), "sony-wh-1000xm5", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(0.0, 5.0, 4.0, 0.0, 6.0)),
    // Sony WH-1000XM5: oratory1990/over-ear/Sony WH-1000XM5 (by oratory1990)
    TunedCurve("WH-1000XM5", listOf("WH-1000XM5"), "sony-wh-1000xm5", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-3.0, -1.0, -4.0, -2.0, 2.0, 3.0, 6.0, -1.0, 2.0, 5.0)),
    // Sony WH-1000XM6: Kuulokenurkka/over-ear/Sony WH-1000XM6 (by Kuulokenurkka)
    TunedCurve("WH-1000XM6", listOf("WH-1000XM6"), "sony-wh-1000xm6", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(0.0, 4.0, -1.0, 6.0, -4.0)),
    // Sony WH-1000XM6: Kuulokenurkka/over-ear/Sony WH-1000XM6 (by Kuulokenurkka)
    TunedCurve("WH-1000XM6", listOf("WH-1000XM6"), "sony-wh-1000xm6", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-3.0, -1.0, -2.0, 0.0, 0.0, 4.0, 0.0, 1.0, 6.0, -5.0)),
    // Sony WF-1000XM3: oratory1990/in-ear/Sony WF-1000XM3 (by oratory1990)
    TunedCurve("WF-1000XM3", listOf("WF-1000XM3"), "sony-wf-1000xm3", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(1.0, 2.0, 2.0, 4.0, -3.0)),
    // Sony WF-1000XM3: oratory1990/in-ear/Sony WF-1000XM3 (by oratory1990)
    TunedCurve("WF-1000XM3", listOf("WF-1000XM3"), "sony-wf-1000xm3", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(3.0, 1.0, -3.0, -2.0, -1.0, 0.0, 0.0, 1.0, 2.0, -6.0)),
    // Sony WF-1000XM4: oratory1990/in-ear/Sony WF-1000XM4 (by oratory1990)
    TunedCurve("WF-1000XM4", listOf("WF-1000XM4"), "sony-wf-1000xm4", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(-3.0, 1.0, 1.0, 6.0, 6.0)),
    // Sony WF-1000XM4: oratory1990/in-ear/Sony WF-1000XM4 (by oratory1990)
    TunedCurve("WF-1000XM4", listOf("WF-1000XM4"), "sony-wf-1000xm4", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-1.0, -1.0, -4.0, -4.0, -2.0, -1.0, 1.0, 1.0, 5.0, 5.0)),
    // Sony WF-1000XM5: oratory1990/in-ear/Sony WF-1000XM5 (by oratory1990)
    TunedCurve("WF-1000XM5", listOf("WF-1000XM5"), "sony-wf-1000xm5", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(2.0, 3.0, 1.0, 3.0, 5.0)),
    // Sony WF-1000XM5: oratory1990/in-ear/Sony WF-1000XM5 (by oratory1990)
    TunedCurve("WF-1000XM5", listOf("WF-1000XM5"), "sony-wf-1000xm5", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(1.0, -1.0, -3.0, -3.0, 0.0, 0.0, -2.0, 1.0, -1.0, 2.0)),
    // Sony LinkBuds: Rtings/HMS II.3 earbud/Sony LinkBuds (by Rtings)
    TunedCurve("LinkBuds", listOf("LinkBuds"), "sony-linkbuds", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(-3.0, -6.0, -4.0, 4.0, -1.0)),
    // Sony LinkBuds: Rtings/HMS II.3 earbud/Sony LinkBuds (by Rtings)
    TunedCurve("LinkBuds", listOf("LinkBuds"), "sony-linkbuds", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(5.0, 4.0, 4.0, 1.0, -5.0, -4.0, -6.0, 3.0, 3.0, -1.0)),
    // Sony LinkBuds S: Rtings/Bruel & Kjaer 5128 in-ear/Sony LinkBuds S (by Rtings)
    TunedCurve("LinkBuds S", listOf("LinkBuds S"), "sony-linkbuds-s", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(1.0, -1.0, -1.0, 3.0, 6.0)),
    // Sony LinkBuds S: Rtings/Bruel & Kjaer 5128 in-ear/Sony LinkBuds S (by Rtings)
    TunedCurve("LinkBuds S", listOf("LinkBuds S"), "sony-linkbuds-s", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-1.0, -2.0, -2.0, -2.0, 3.0, -2.0, -1.0, 0.0, 4.0, 6.0)),
    // Sony LinkBuds Fit: DHRME/in-ear/Sony LinkBuds Fit (by DHRME)
    TunedCurve("LinkBuds Fit", listOf("LinkBuds Fit"), "sony-linkbuds-fit", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(0.0, -2.0, 1.0, 0.0, 2.0)),
    // Sony LinkBuds Fit: DHRME/in-ear/Sony LinkBuds Fit (by DHRME)
    TunedCurve("LinkBuds Fit", listOf("LinkBuds Fit"), "sony-linkbuds-fit", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(0.0, 0.0, -2.0, 0.0, -1.0, -2.0, 0.0, 2.0, -1.0, 2.0)),
    // Sony WF-C500: oratory1990/in-ear/Sony WF-C500 (by oratory1990)
    TunedCurve("WF-C500", listOf("WF-C500"), "sony-wf-c500", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(0.0, 1.0, -5.0, 3.0, -3.0)),
    // Sony WF-C500: oratory1990/in-ear/Sony WF-C500 (by oratory1990)
    TunedCurve("WF-C500", listOf("WF-C500"), "sony-wf-c500", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-3.0, 1.0, 2.0, 1.0, 2.0, 3.0, -3.0, 3.0, 2.0, -1.0)),
    // Sony WF-C700N: Rtings/HMS II.3 in-ear/Sony WF-C700N (by Rtings)
    TunedCurve("WF-C700N", listOf("WF-C700N"), "sony-wf-c700n", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(1.0, -1.0, 1.0, 1.0, 6.0)),
    // Sony WF-C700N: Rtings/HMS II.3 in-ear/Sony WF-C700N (by Rtings)
    TunedCurve("WF-C700N", listOf("WF-C700N"), "sony-wf-c700n", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-2.0, -3.0, -2.0, -2.0, 0.0, -2.0, -2.0, 1.0, -3.0, 5.0)),
    // Sony WH-CH520: Rtings/HMS II.3 over-ear/Sony WH-CH520 (by Rtings)
    TunedCurve("WH-CH520", listOf("WH-CH520"), "sony-wh-ch520", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(1.0, 0.0, 0.0, 5.0, -4.0)),
    // Sony WH-CH520: Rtings/HMS II.3 over-ear/Sony WH-CH520 (by Rtings)
    TunedCurve("WH-CH520", listOf("WH-CH520"), "sony-wh-ch520", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(3.0, -3.0, -1.0, -1.0, -1.0, -1.0, -2.0, 2.0, 0.0, -5.0)),
    // Sony WH-CH710N: Rtings/HMS II.3 over-ear/Sony WH-CH710N (by Rtings)
    TunedCurve("WH-CH710N", listOf("WH-CH710N"), "sony-wh-ch710n", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(0.0, 4.0, 0.0, 2.0, 5.0)),
    // Sony WH-CH710N: Rtings/HMS II.3 over-ear/Sony WH-CH710N (by Rtings)
    TunedCurve("WH-CH710N", listOf("WH-CH710N"), "sony-wh-ch710n", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(0.0, -2.0, -3.0, -2.0, 0.0, 2.0, -1.0, 0.0, -1.0, 3.0)),
    // Sony WH-CH720N: Rtings/Bruel & Kjaer 5128 over-ear/Sony WH-CH720N (by Rtings)
    TunedCurve("WH-CH720N", listOf("WH-CH720N"), "sony-wh-ch720n", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(2.0, 6.0, 4.0, 0.0, 6.0)),
    // Sony WH-CH720N: Rtings/Bruel & Kjaer 5128 over-ear/Sony WH-CH720N (by Rtings)
    TunedCurve("WH-CH720N", listOf("WH-CH720N"), "sony-wh-ch720n", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-1.0, 0.0, -5.0, -3.0, 2.0, 4.0, 1.0, 4.0, -5.0, 6.0)),
    // Sony WH-XB900N: Rtings/HMS II.3 over-ear/Sony WH-XB900N (by Rtings)
    TunedCurve("WH-XB900N", listOf("WH-XB900N"), "sony-wh-xb900n", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(6.0, 5.0, 2.0, 2.0, 2.0)),
    // Sony WH-XB900N: Rtings/HMS II.3 over-ear/Sony WH-XB900N (by Rtings)
    TunedCurve("WH-XB900N", listOf("WH-XB900N"), "sony-wh-xb900n", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-4.0, -3.0, -3.0, 1.0, 6.0, 4.0, -1.0, 5.0, -2.0, 3.0)),
    // Sony WH-XB910N: Rtings/Bruel & Kjaer 5128 over-ear/Sony WH-XB910N (by Rtings)
    TunedCurve("WH-XB910N", listOf("WH-XB910N"), "sony-wh-xb910n", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(6.0, 0.0, 6.0, 2.0, 1.0)),
    // Sony WH-XB910N: Rtings/Bruel & Kjaer 5128 over-ear/Sony WH-XB910N (by Rtings)
    TunedCurve("WH-XB910N", listOf("WH-XB910N"), "sony-wh-xb910n", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-6.0, -6.0, -5.0, 0.0, 6.0, 0.0, 0.0, 6.0, -3.0, 1.0)),
    // Sony ULT WEAR: Rtings/Bruel & Kjaer 5128 over-ear/Sony ULT WEAR (by Rtings)
    TunedCurve("ULT WEAR", listOf("ULT WEAR"), "sony-ult-wear", listOf(400, 1000, 2500, 6300, 16000), 1.0, listOf(0.0, 1.0, 6.0, 1.0, 4.0)),
    // Sony ULT WEAR: Rtings/Bruel & Kjaer 5128 over-ear/Sony ULT WEAR (by Rtings)
    TunedCurve("ULT WEAR", listOf("ULT WEAR"), "sony-ult-wear", listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), 1.0, listOf(-5.0, -3.0, -6.0, 0.0, 2.0, 1.0, 4.0, 6.0, -1.0, 5.0)),
    // Nothing ear (2): Rtings/HMS II.3 in-ear/Nothing ear (2) (by Rtings)
    TunedCurve("Ear (2)", listOf("Nothing Ear (2)"), "nothing-ear-2", listOf(55, 110, 220, 440, 1320, 3300, 6600, 13200), 0.1, listOf(0.4, -0.5, -0.7, 3.3, -0.2, -0.5, -1.2, -4.4)),
    // Nothing ear (2): Rtings/HMS II.3 in-ear/Nothing ear (2) (by Rtings)
    TunedCurve("Ear (2)", listOf("Nothing Ear (2)"), "nothing-ear-2", listOf(140, 980, 3400), 1.0, listOf(0.0, 2.0, -2.0)),
    // Nothing ear: Rtings/Bruel & Kjaer 5128 in-ear/Nothing ear (by Rtings)
    TunedCurve("Ear", listOf("Nothing Ear"), "nothing-ear", listOf(55, 110, 220, 440, 1320, 3300, 6600, 13200), 0.1, listOf(-1.6, -0.5, -2.0, 0.7, 0.3, 0.4, -2.1, 5.6)),
    // Nothing ear: Rtings/Bruel & Kjaer 5128 in-ear/Nothing ear (by Rtings)
    TunedCurve("Ear", listOf("Nothing Ear"), "nothing-ear", listOf(140, 980, 3400), 1.0, listOf(2.0, 4.0, 2.0)),
    // Nothing ear (a): Rtings/Bruel & Kjaer 5128 in-ear/Nothing ear (a) (by Rtings)
    TunedCurve("Ear (a)", listOf("Nothing Ear (a)"), "nothing-ear-a", listOf(140, 980, 3400), 1.0, listOf(0.0, 4.0, 0.0)),
    // Nothing ear (stick): Rtings/HMS II.3 in-ear/Nothing ear (stick) (by Rtings)
    TunedCurve("Ear (stick)", listOf("Nothing Ear (stick)"), "nothing-ear-stick", listOf(55, 110, 220, 440, 1320, 3300, 6600, 13200), 0.1, listOf(-0.5, -0.4, -2.2, -4.2, -0.5, -4.2, -6.0, -4.8)),
    // Nothing ear (stick): Rtings/HMS II.3 in-ear/Nothing ear (stick) (by Rtings)
    TunedCurve("Ear (stick)", listOf("Nothing Ear (stick)"), "nothing-ear-stick", listOf(140, 980, 3500), 1.0, listOf(6.0, 6.0, 2.0)),
    // CMF by Nothing Buds Pro 2: Rtings/Bruel & Kjaer 5128 in-ear/CMF by Nothing Buds Pro 2 (by Rtings)
    TunedCurve("CMF Buds Pro 2", listOf("CMF Buds Pro 2"), "cmf-by-nothing-buds-pro-2", listOf(140, 980, 6900), 1.0, listOf(-5.0, 5.0, 2.0)),
    // OnePlus Buds Pro: Rtings/HMS II.3 in-ear/OnePlus Buds Pro (by Rtings)
    TunedCurve("OnePlus Buds Pro", listOf("OnePlus Buds Pro"), "oneplus-buds-pro", listOf(62, 250, 1000, 4000, 8000, 16000), 1.0, listOf(2.0, 5.0, 2.0, 2.0, -2.0, -2.0)),
    // OnePlus Buds Pro 2: Rtings/HMS II.3 in-ear/OnePlus Buds Pro 2 (by Rtings)
    TunedCurve("OnePlus Buds Pro 2", listOf("OnePlus Buds Pro 2"), "oneplus-buds-pro-2", listOf(62, 250, 1000, 4000, 8000, 16000), 1.0, listOf(-1.0, 1.0, 3.0, 3.0, -1.0, -5.0)),
    // OnePlus Buds 3: Rtings/Bruel & Kjaer 5128 in-ear/OnePlus Buds 3 (by Rtings)
    TunedCurve("OnePlus Buds 3", listOf("OnePlus Buds 3"), "oneplus-buds-3", listOf(62, 250, 1000, 4000, 8000, 16000), 1.0, listOf(-3.0, 3.0, 5.0, 0.0, 5.0, -6.0)),
)
