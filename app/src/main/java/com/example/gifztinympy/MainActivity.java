package com.example.gif2tinymp4;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DropView dropView;
    private ImageView preview;
    private TextView status;
    private TextView details;
    private ProgressBar progress;
    private Button saveButton;
    private Button shareButton;
    private File currentMp4;
    private Uri currentSavedUri;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("GIF → Tiny MP4");
        title.setTextSize(27); title.setTextColor(Color.BLACK); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(55)));

        TextView sub = new TextView(this);
        sub.setText("Pick a GIF from Gboard. It converts locally into a very small H.264 MP4.");
        sub.setTextSize(14); sub.setTextColor(Color.DKGRAY); sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.bottomMargin = dp(16); root.addView(sub, sp);

        dropView = new DropView(this);
        dropView.setBackgroundColor(Color.rgb(247,247,247));
        root.addView(dropView, new LinearLayout.LayoutParams(-1, dp(210)));

        status = text("Waiting for GIF…", 16, Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true); preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(190)); pp.topMargin = dp(12); root.addView(preview, pp);

        details = text("", 13, Color.DKGRAY);
        details.setGravity(Gravity.CENTER);
        root.addView(details, new LinearLayout.LayoutParams(-1, dp(45)));

        saveButton = new Button(this); saveButton.setText("SAVE MP4"); saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveCurrent());
        root.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(52)));

        shareButton = new Button(this); shareButton.setText("SHARE MP4"); shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareCurrent());
        root.addView(shareButton, new LinearLayout.LayoutParams(-1, dp(52)));

        setContentView(scroll);
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void receiveGif(final Uri uri) {
        runOnUiThread(() -> { status.setText("GIF received — converting…"); progress.setVisibility(View.VISIBLE); progress.setProgress(5); saveButton.setEnabled(false); shareButton.setEnabled(false); });
        executor.execute(() -> {
            File out = null;
            try {
                out = new File(getCacheDir(), "tiny_" + System.currentTimeMillis() + ".mp4");
                Movie movie;
                try (InputStream in = new BufferedInputStream(getContentResolver().openInputStream(uri))) {
                    if (in == null) throw new Exception("Could not open GIF");
                    movie = Movie.decodeStream(in);
                }
                if (movie == null || movie.width() <= 0 || movie.height() <= 0) throw new Exception("The keyboard did not provide a decodable GIF.");
                TinyEncoder.encode(movie, out, p -> runOnUiThread(() -> progress.setProgress(Math.min(98, Math.max(5, p)))));
                currentMp4 = out;
                final long bytes = out.length();
                final Bitmap first = firstFrame(movie);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (first != null) preview.setImageBitmap(first);
                    status.setText("MP4 ready ✓");
                    details.setText(String.format(Locale.US, "%d × %d  •  %.1f KB  •  H.264  •  no audio", movie.width() + (movie.width() & 1), movie.height() + (movie.height() & 1), bytes / 1024.0));
                    saveButton.setEnabled(true); shareButton.setEnabled(true);
                });
            } catch (Throwable e) {
                if (out != null) out.delete();
                final String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> { progress.setVisibility(View.GONE); status.setText("Conversion failed"); Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private Bitmap firstFrame(Movie m) {
        int w = (m.width() + 1) & ~1, h = (m.height() + 1) & ~1;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); c.drawColor(Color.BLACK, android.graphics.PorterDuff.Mode.CLEAR); m.setTime(0); m.draw(c, 0, 0); return b;
    }

    private void saveCurrent() {
        if (currentMp4 == null || !currentMp4.exists()) return;
        executor.execute(() -> {
            try {
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.DISPLAY_NAME, "GIF_" + System.currentTimeMillis() + ".mp4");
                    v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GIF2TinyMP4");
                    v.put(MediaStore.Video.Media.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Gallery refused the file");
                    try (InputStream in = new FileInputStream(currentMp4); OutputStream out = getContentResolver().openOutputStream(uri)) { copy(in, out); }
                    ContentValues done = new ContentValues(); done.put(MediaStore.Video.Media.IS_PENDING, 0); getContentResolver().update(uri, done, null, null);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "GIF2TinyMP4");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create Movies folder");
                    File f = new File(dir, "GIF_" + System.currentTimeMillis() + ".mp4");
                    try (InputStream in = new FileInputStream(currentMp4); OutputStream out = new FileOutputStream(f)) { copy(in, out); }
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
                    uri = Uri.fromFile(f);
                }
                currentSavedUri = uri;
                runOnUiThread(() -> Toast.makeText(this, "Saved to Movies/GIF2TinyMP4", Toast.LENGTH_SHORT).show());
            } catch (Throwable e) { runOnUiThread(() -> Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        });
    }

    private void shareCurrent() {
        if (currentMp4 == null || !currentMp4.exists()) return;
        // FileProvider would add a dependency/manifest surface; use a MediaStore copy first on modern Android.
        executor.execute(() -> {
            try {
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.DISPLAY_NAME, "GIF_share_" + System.currentTimeMillis() + ".mp4");
                    v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GIF2TinyMP4");
                    v.put(MediaStore.Video.Media.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Gallery refused the file");
                    try (InputStream in = new FileInputStream(currentMp4); OutputStream out = getContentResolver().openOutputStream(uri)) { copy(in, out); }
                    ContentValues done = new ContentValues(); done.put(MediaStore.Video.Media.IS_PENDING, 0); getContentResolver().update(uri, done, null, null);
                } else throw new Exception("Use SAVE MP4 on Android 9 or older, then share it from Gallery.");
                Intent i = new Intent(Intent.ACTION_SEND); i.setType("video/mp4"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(i, "Share MP4")));
            } catch (Throwable e) { runOnUiThread(() -> Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        });
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        if (out == null) throw new Exception("Could not open output");
        byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); out.flush();
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private final class DropView extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        DropView(Context c) { super(c); setFocusableInTouchMode(true); p.setTextAlign(android.graphics.Paint.Align.CENTER); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); p.setColor(Color.rgb(80,80,80)); p.setTextSize(dp(20)); c.drawText("Tap here → open Gboard", getWidth()/2f, getHeight()/2f - dp(8), p); p.setTextSize(dp(14)); c.drawText("Choose a GIF and it will convert automatically", getWidth()/2f, getHeight()/2f + dp(22), p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) { requestFocus(); InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE); imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT); return true; }
            return true;
        }
        @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE;
            outAttrs.contentMimeTypes = new String[]{"image/gif", "image/*"};
            return new BaseInputConnection(this, false) {
                @Override public boolean commitContent(InputContentInfo info, int flags, android.os.Bundle opts) {
                    if (Build.VERSION.SDK_INT >= 25 && (flags & InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                        try { info.requestPermission(); } catch (Exception ignored) {}
                    }
                    Uri u = info.getContentUri();
                    if (u != null) { receiveGif(u); return true; }
                    return false;
                }
            };
        }
        @Override public boolean onCheckIsTextEditor() { return true; }
    }

    interface Progress { void onProgress(int p); }

    static final class TinyEncoder {
        static void encode(Movie movie, File output, Progress progress) throws Exception {
            int srcW = movie.width(), srcH = movie.height();
            int w = (srcW + 1) & ~1, h = (srcH + 1) & ~1;
            int fps = 10;
            int duration = movie.duration(); if (duration <= 0) duration = 1000;
            int frames = Math.max(1, (int)Math.ceil(duration / 1000.0 * fps));

            MediaCodec codec = null; MediaMuxer muxer = null; int track = -1; boolean muxStarted = false;
            try {
                codec = createEncoder(w, h, fps);
                int color = chooseColor(codec.getCodecInfo().getCapabilitiesForType("video/avc").colorFormats);
                if (color == 0) throw new Exception("No supported YUV420 encoder input format.");
                muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                codec.start();

                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                long frameDurationUs = 1_000_000L / fps;
                boolean inputDone = false, outputDone = false;
                int fed = 0;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                while (!outputDone) {
                    if (!inputDone) {
                        int idx = codec.dequeueInputBuffer(10000);
                        if (idx >= 0) {
                            ByteBuffer in = codec.getInputBuffer(idx);
                            if (fed < frames) {
                                int ms = (int)Math.min(duration - 1, Math.round(fed * 1000.0 / fps));
                                canvas.drawColor(Color.BLACK, android.graphics.PorterDuff.Mode.CLEAR); movie.setTime(Math.max(0, ms)); movie.draw(canvas, 0, 0);
                                byte[] yuv = bitmapToYuv420(bitmap, color);
                                in.clear(); in.put(yuv);
                                codec.queueInputBuffer(idx, 0, yuv.length, fed * frameDurationUs, 0);
                                fed++; if (progress != null) progress.onProgress(5 + fed * 45 / frames);
                            } else {
                                codec.queueInputBuffer(idx, 0, 0, fed * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true;
                            }
                        }
                    }
                    int outIdx = codec.dequeueOutputBuffer(info, 10000);
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxStarted) throw new Exception("Encoder changed format twice");
                        track = muxer.addTrack(codec.getOutputFormat()); muxer.start(); muxStarted = true;
                    } else if (outIdx >= 0) {
                        ByteBuffer out = codec.getOutputBuffer(outIdx);
                        if (out != null && info.size > 0 && muxStarted && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            out.position(info.offset); out.limit(info.offset + info.size); muxer.writeSampleData(track, out, info);
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                        codec.releaseOutputBuffer(outIdx, false);
                        if (progress != null) progress.onProgress(50 + Math.min(49, fed * 49 / frames));
                    }
                }
            } finally {
                if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} try { codec.release(); } catch (Exception ignored) {} }
                if (muxer != null) { try { if (muxStarted) muxer.stop(); } catch (Exception ignored) {} try { muxer.release(); } catch (Exception ignored) {} }
            }
        }

        private static MediaCodec createEncoder(int w, int h, int fps) throws Exception {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo chosen = null;
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                for (String t : info.getSupportedTypes()) if ("video/avc".equalsIgnoreCase(t)) { chosen = info; break; }
                if (chosen != null) break;
            }
            if (chosen == null) throw new Exception("No H.264 encoder is available on this phone.");
            MediaCodecInfo.CodecCapabilities caps = chosen.getCapabilitiesForType("video/avc");
            int color = chooseColor(caps.colorFormats);
            if (color == 0) throw new Exception("This phone's H.264 encoder does not expose a supported YUV input mode.");
            MediaFormat f = MediaFormat.createVideoFormat("video/avc", w, h);
            f.setInteger(MediaFormat.KEY_COLOR_FORMAT, color);
            f.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            // Tiny-output bias: roughly 120 kbps at 220x170/10fps, scaled for larger frames.
            int bitrate = Math.max(80000, Math.min(260000, (int)(w * h * fps * 0.004f)));
            f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 4);
            f.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
            MediaCodec codec = MediaCodec.createByCodecName(chosen.getName());
            codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            return codec;
        }

        private static int chooseColor(int[] formats) {
            int flexible = 0x7f420888;
            for (int x : formats) if (x == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) return x;
            for (int x : formats) if (x == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) return x;
            return 0;
        }

        private static byte[] bitmapToYuv420(Bitmap b, int colorFormat) {
            int w=b.getWidth(), h=b.getHeight(); int[] px=new int[w*h]; b.getPixels(px,0,w,0,0,w,h);
            int frame=w*h; byte[] out=new byte[frame + frame/2]; int y=0, u=frame, v=frame+frame/4;
            boolean semi = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            int uvIndex=frame;
            for(int j=0;j<h;j++) for(int i=0;i<w;i++) {
                int c=px[j*w+i]; int r=(c>>16)&255,g=(c>>8)&255,bl=c&255;
                int yy=((66*r+129*g+25*bl+128)>>8)+16; int uu=((-38*r-74*g+112*bl+128)>>8)+128; int vv=((112*r-94*g-18*bl+128)>>8)+128;
                out[y++]=(byte)Math.max(0,Math.min(255,yy));
                if((j&1)==0 && (i&1)==0) {
                    if(semi){ out[uvIndex++]=(byte)Math.max(0,Math.min(255,uu)); out[uvIndex++]=(byte)Math.max(0,Math.min(255,vv)); }
                    else { out[u++]=(byte)Math.max(0,Math.min(255,uu)); out[v++]=(byte)Math.max(0,Math.min(255,vv)); }
                }
            }
            return out;
        }
    }
}
