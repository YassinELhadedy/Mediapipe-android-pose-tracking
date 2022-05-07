//package com.example.mediapipeposetracking;
//
//import static java.util.Objects.requireNonNull;
//
//import android.annotation.SuppressLint;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.Choreographer;
//import android.view.GestureDetector;
//import android.view.MotionEvent;
//import android.view.SurfaceView;
//import android.view.View;
//import android.view.WindowManager;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.google.android.filament.Engine;
//import com.google.android.filament.RenderableManager;
//import com.google.android.filament.Renderer;
//import com.google.android.filament.Scene;
//import com.google.android.filament.android.UiHelper;
//import com.google.android.filament.gltfio.FilamentAsset;
//import com.google.android.filament.utils.AutomationEngine;
//import com.google.android.filament.utils.Float3;
//import com.google.android.filament.utils.KTXLoader;
//import com.google.android.filament.utils.Manipulator;
//import com.google.android.filament.utils.ModelViewer;
//import com.google.android.filament.utils.Utils;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.nio.ByteBuffer;
//
//public class MainActivity2 extends AppCompatActivity {
//
//    private SurfaceView surfaceView;
//    private Choreographer choreographer;
//    private GestureDetector doubleTapDeteector;
//    private AutomationEngine automation ;
//    private ModelViewer modelViewer;
//    private RenderableManager rm;
//    private FrameCallback frameScheduler;
//    private AutomationEngine.ViewerContent viewerContent= new AutomationEngine.ViewerContent();
//    @SuppressLint("ClickableViewAccessibility")
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        Utils.INSTANCE.init();
//        automation = new  AutomationEngine();
//        surfaceView = findViewById(R.id.main_sv);
//        choreographer = Choreographer.getInstance();
//
//
//        Manipulator manipulator;
//        manipulator = new Manipulator.Builder()
//                //.targetPosition(0.0f, 0.0f, -4.0f)
//                //.orbitHomePosition(0f, 0.0f, -1.25f)
//                .build(Manipulator.Mode.ORBIT);
//        modelViewer = new ModelViewer(surfaceView, Engine.create(),new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK),manipulator);
//        viewerContent.view = modelViewer.getView();
//        viewerContent.sunlight = modelViewer.getLight();
//        viewerContent.lightManager = modelViewer.getEngine().getLightManager();
//        viewerContent.scene = modelViewer.getScene();
//        viewerContent.renderer = modelViewer.getRenderer();
//        frameScheduler =new  FrameCallback();
//        Renderer.ClearOptions options = viewerContent.renderer.getClearOptions();
//        options.clear = true;
//        viewerContent.renderer.setClearOptions(options);
//        rm = modelViewer.getEngine().getRenderableManager();
//        doubleTapDeteector = new GestureDetector(getApplicationContext(), new DoubleTapListener());
//        surfaceView.setOnTouchListener( new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View view, MotionEvent motionEvent) {
//                modelViewer.onTouchEvent(motionEvent);
//                doubleTapDeteector.onTouchEvent(motionEvent);
//                return true;
//            }
//        });
//        createDefaultRenderables();
//        createIndirectLight();
//    }
//
//    private void createDefaultRenderables() {
//        ByteBuffer buffer;
//        try {
//            InputStream inFile = getAssets().open("models/hoodWithIKFixedCoordinates.glb");
//            int size = inFile.available();
//            buffer = ByteBuffer.allocate(size);
//            inFile.read(buffer.array());
//            ByteBuffer.wrap(buffer.array());
//            modelViewer.loadModelGlb(buffer);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        updateRootTransform();
//    }
//    private ByteBuffer readCompressedAsset( String assetName)  {
//        InputStream input = null;
//        ByteBuffer bytes = null;
//        try {
//            input = getAssets().open(assetName);
//            bytes = ByteBuffer.allocate(input.available());
//            input.read(bytes.array());
//            return ByteBuffer.wrap(bytes.array());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return bytes;
//    }
//    private void createIndirectLight() {
//        Engine engine = modelViewer.getEngine();
//        Scene scene = modelViewer.getScene();
//
//        ByteBuffer buf =  readCompressedAsset("envs/default_env_ibl.ktx");
//        scene.setIndirectLight(  KTXLoader.INSTANCE.createIndirectLight(engine, requireNonNull(buf), new KTXLoader.Options()));
//        scene.getIndirectLight().setIntensity( 30000.0f);
//        viewerContent.indirectLight = modelViewer.getScene().getIndirectLight();
//    }
//    private void updateRootTransform() {
//        if (automation.getViewerOptions().autoScaleEnabled) {
//            modelViewer.transformToUnitCube(new Float3(0f,0f,0));
//            //4modelViewer.getAsset().getAnimator().updateBoneMatrices();
//        } else {
//            modelViewer.clearRootTransform();
//        }
//    }
//    @Override
//    public void onResume() {
//        super.onResume();
//        choreographer.postFrameCallback(frameScheduler);
//    }
//
//    @Override
//    public void  onPause() {
//        super.onPause();
//        choreographer.removeFrameCallback(frameScheduler);
//    }
//
//    @Override
//    public void  onDestroy() {
//        super.onDestroy();
//        choreographer.removeFrameCallback(frameScheduler);
//    }
//
//
//    class FrameCallback implements Choreographer.FrameCallback {
//        private long  startTime = System.nanoTime();
//
//        @Override
//        public void doFrame(long frameTimeNanos) {
//            choreographer.postFrameCallback(this);
//            modelViewer.render(frameTimeNanos);
//            FilamentAsset name = modelViewer.getAsset();
//            int [] count = name.getEntities();
//            for (int i = 0; i < count.length; i++)
//            {
//                String str = name.getName(i);
//                if(str!=null)
//                    Log.i("#man6",name.getName(i).toString());
//            }
//            //modelViewer.
//            //Log.i("#man5",name[0].toString());
//            //int [] Ents = modelViewer.getAsset().getJointsAt(0);
//            //FloatBuffer transformMatrix = FloatBuffer.allocate(16);
//            // android.opengl.Matrix.setRotateM(transformMatrix.array(), 0, 90f, 1.0f, 0.0f, 0.0f);
//            //int [] Ents = modelViewer.getAsset().getEntitiesByName("forearmIK.L");
//            //modelViewer.getEngine().getRenderableManager().setBonesAsMatrices(Ents[0],transformMatrix,1,1 );
//            //int bone = rm.getInstance(Ents[0]);
//
//        }
//    }
//
//    class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
//        @Override
//        public boolean onDoubleTap(MotionEvent e) {
//            modelViewer.destroyModel();
//            createDefaultRenderables();
//            return super.onDoubleTap(e);
//        }
//    }
//}