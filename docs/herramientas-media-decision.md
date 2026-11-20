# Decisión de conversión local

La rama `herramientas` usa `dev.ffmpegkit-maintained:ffmpeg-kit-full:6.0.3`, variante no-GPL con licencia LGPL-3.0. La variante 8.1.7 exigía minSdk 24; la LTS 6.0.3 también declaró API 24, por lo que se autorizó subir la aplicación de minSdk 23 a minSdk 24. No se usa `tools:overrideLibrary`.

Media3 Transformer no cubre por sí solo todo el conjunto requerido por la especificación. La documentación oficial indica salida MP4 por defecto y muxers adicionales para WebM, AAC, Ogg y WAV; no cubre de forma completa MKV, AVI, MOV y los encoders de audio solicitados. FFmpegKit mantenido permite conversión local amplia mediante FFmpeg sin enviar archivos a servidores. Se usa la variante LGPL, no las variantes `-gpl`.

Fuentes verificadas el 2026-11-20:

- https://developer.android.com/media/media3/transformer/supported-formats
- https://github.com/arthenica/ffmpeg-kit
- https://github.com/ffmpegkit-maintained/ffmpeg-kit
