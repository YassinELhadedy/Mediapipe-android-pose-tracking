package com.example.mediapipeposetracking;

import static com.google.android.filament.utils.MatrixKt.translation;
import static com.google.android.filament.utils.MatrixKt.transpose;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.Viewport;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.utils.AutomationEngine;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.KTXLoader;
import com.google.android.filament.utils.Manipulator;
import com.google.android.filament.utils.Mat4;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Main activity of MediaPipe example apps.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "YXH";
    private static final String BINARY_GRAPH_NAME = "pose_tracking_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks";


    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.BACK;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    private SurfaceView previewDisplayView2;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;

    private Choreographer choreographer;
    private final MainActivity.FrameCallback frameScheduler = new MainActivity.FrameCallback();
    private ModelViewer modelViewer;
    private final MainActivity.DoubleTapListener doubleTapListener = new MainActivity.DoubleTapListener();
    private GestureDetector doubleTapDetector;
    private final AutomationEngine.ViewerContent viewerContent = new AutomationEngine.ViewerContent();
    private Manipulator manipulator;

    private UiHelper uiHelper;
    private SwapChain swapChain;
    private DisplayHelper displayHelper;
    private NormalizedLandmarkList landmarks = null;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());
        Utils.INSTANCE.init();
        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        previewDisplayView = new SurfaceView(this);
        previewDisplayView2 = new SurfaceView(this);
        previewDisplayView2.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        choreographer = Choreographer.getInstance();
        displayHelper = new DisplayHelper(this);

        doubleTapDetector = new GestureDetector(this.getApplicationContext(), doubleTapListener);

        manipulator = new Manipulator.Builder()
                //.targetPosition(0.0f, 0.0f, -4.0f)
                //.orbitHomePosition(0f, 0.0f, -1.25f)
                .build(Manipulator.Mode.ORBIT);



        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);

        uiHelper.setRenderCallback(new SurfaceCallback());

        // Make the render target transparent
        uiHelper.setOpaque(false);

        uiHelper.attachTo(previewDisplayView2);


        modelViewer = new ModelViewer(previewDisplayView2,Engine.create(),uiHelper,manipulator);
        viewerContent.view = modelViewer.getView();
        modelViewer.getView().setBlendMode(com.google.android.filament.View.BlendMode.TRANSLUCENT);
        viewerContent.sunlight = modelViewer.getLight();
        viewerContent.lightManager = modelViewer.getEngine().getLightManager();
        viewerContent.scene = modelViewer.getScene();
        viewerContent.renderer = modelViewer.getRenderer();



        Renderer.ClearOptions options = viewerContent.renderer.getClearOptions();

        options.clear = true;
        viewerContent.renderer.setClearOptions(options);



        previewDisplayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                //modelViewer.onTouchEvent(motionEvent);
                doubleTapDetector.onTouchEvent(motionEvent);
                return true;
            }
        });



        setupPreviewDisplayView();

        createDefaultRenderables();
        createIndirectLight();


        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);

        PermissionHelper.checkAndRequestCameraPermissions(this);
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    Log.v(TAG, "Received multi-hand landmarks packet.");

                    Log.v(TAG, packet.toString());
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        landmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        if (landmarks == null) {
                            Log.v(TAG, "[TS:" + packet.getTimestamp() + "] No iris landmarks.");
                            return;
                        }
                        // Note: If eye_presence is false, these landmarks are useless.
                        Log.v(
                                TAG,
                                "[TS:"
                                        + packet.getTimestamp()
                                        + "] #Landmarks for iris: "
                                        + landmarks.getLandmarkCount());
                        Log.v("@LandM", getLandmarksDebugString(landmarks));
                    } catch (InvalidProtocolBufferException e) {
                        Log.e(TAG, "Couldn't Exception received - " + e);
                        return;
                    }
                });
    }

    private void createIndirectLight() {
        Engine engine = modelViewer.getEngine();
        Scene scene = modelViewer.getScene();

        ByteBuffer buf =  readCompressedAsset("envs/default_env_ibl.ktx");
        scene.setIndirectLight(  KTXLoader.INSTANCE.createIndirectLight(engine, requireNonNull(buf), new KTXLoader.Options()));
        scene.getIndirectLight().setIntensity( 30000.0f);
        viewerContent.indirectLight = modelViewer.getScene().getIndirectLight();
    }

    private ByteBuffer readCompressedAsset( String assetName)  {
        InputStream input = null;
        ByteBuffer bytes = null;
        try {
            input = getAssets().open(assetName);
            bytes = ByteBuffer.allocate(input.available());
            input.read(bytes.array());
            return ByteBuffer.wrap(bytes.array());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }


    private static String getLandmarksDebugString(NormalizedLandmarkList landmarks) {
        int landmarkIndex = 0;
        StringBuilder landmarksString = new StringBuilder();
        for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
            landmarksString.append("\t\tLandmark[").append(landmarkIndex).append("]: (").append(landmark.getX()).append(", ").append(landmark.getY()).append(", ").append(landmark.getZ()).append(")\n");
            ++landmarkIndex;
        }
        return landmarksString.toString();
    }

    // Used to obtain the content view for this application. If you are extending this class, and
    // have a custom layout, override this method and return the custom layout.
    protected int getContentViewLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        choreographer.postFrameCallback(frameScheduler);

        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();

        choreographer.removeFrameCallback(frameScheduler);

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        choreographer.removeFrameCallback(frameScheduler);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                this::onCameraStarted);
        CameraHelper.CameraFacing cameraFacing = CAMERA_FACING;
        cameraHelper.startCamera(
                this, cameraFacing, /*unusedSurfaceTexture=*/ null, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);

        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
        previewDisplayView2.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        viewGroup.addView(previewDisplayView2);
    }


    private void createDefaultRenderables() {
        ByteBuffer buffer;
        try {
            InputStream inFile = getAssets().open("models/LandMarkPoints.glb");
            int size = inFile.available();
            buffer = ByteBuffer.allocate(size);
            inFile.read(buffer.array());
            ByteBuffer.wrap(buffer.array());
            modelViewer.loadModelGlb(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateRootTransform();
    }


    private void updateRootTransform() {
        if (true) {
            modelViewer.transformToUnitCube(new Float3(0f, 0f, 0));
        } else {
            modelViewer.clearRootTransform();
        }
    }


    public final class SurfaceCallback implements UiHelper.RendererCallback{

        @Override
        public void onNativeWindowChanged(Surface surface) {

            if (swapChain !=null){
                modelViewer.getEngine().destroySwapChain(swapChain);
            }
            displayHelper.attach(modelViewer.getRenderer(), previewDisplayView2.getDisplay());

        }

        @Override
        public void onDetachedFromSurface() {

            displayHelper.detach();
            if (swapChain !=null){
                modelViewer.getEngine().destroySwapChain(swapChain);
                modelViewer.getEngine().flushAndWait();
                swapChain = null;

            }
        }
        @Override
        public void onResized(int width, int height) {

            float zoom = 10f;
            double aspect = (double) width /(double) height;
            modelViewer.getCamera().setProjection(Camera.Projection.ORTHO,
                    -aspect * zoom, aspect * zoom, -zoom, zoom, 0.0, 100);

            modelViewer.getView().setViewport( new Viewport(0, 0, width, height));
        }
    }
    public final class FrameCallback implements android.view.Choreographer.FrameCallback {
        private final long startTime = System.nanoTime();

        public void doFrame(long frameTimeNanos) {
            choreographer.postFrameCallback(this);
//            if (uiHelper.isReadyToRender()&& swapChain!= null){
//                if (modelViewer.getRenderer().beginFrame(swapChain,frameTimeNanos)){
//                    modelViewer.getRenderer().render(viewerContent.view);
//                    modelViewer.getRenderer().endFrame();
//                }
//           }

            int []ent =  modelViewer.getAsset().getEntities();


            Mat4 resTrans = translation(new Float3(0.0f,0.0f,-30f));
            for (int i = 0; i < 16; i++)
            {
                Log.i("#MAT",String.valueOf(i)+" : "+ String.valueOf(resTrans.toFloatArray()[i]));
            }
            if(landmarks!= null)
            {
                for (int i = 0; i < landmarks.getLandmarkCount(); i ++)
                {
                    int neckBoneInstance = modelViewer.getEngine().getTransformManager().getInstance(ent[i]);

                    LandmarkProto.NormalizedLandmark lM =  landmarks.getLandmark(i);
                    resTrans = translation(new Float3((float)((lM.getX()-0.5)*58),(float)-(lM.getY()-0.5)*(65*1.3f),(float)((lM.getZ())*2)-100));

                    modelViewer.getEngine().getTransformManager().setTransform(neckBoneInstance, transpose(resTrans).toFloatArray());
                }
            }




            modelViewer.getAnimator().updateBoneMatrices();


            modelViewer.render(frameTimeNanos);

        }
    }

    public final class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
        public boolean onDoubleTap(@Nullable MotionEvent e) {
            modelViewer.destroyModel();
            createDefaultRenderables();
            return super.onDoubleTap(e);
        }
    }

}