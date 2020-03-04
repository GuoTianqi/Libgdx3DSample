package com.mygdx.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class ShaderTest implements ApplicationListener {
    public PerspectiveCamera cam;
    public CameraInputController camController;
    public Shader shader;
    public Model model;
    public Array<ModelInstance> instances = new Array<ModelInstance>();
    public ModelBatch modelBatch;

    @Override
    public void create () {
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0f, 8f, 8f);
        cam.lookAt(0,0,0);
        cam.near = 1f;
        cam.far = 300f;
        cam.update();

        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(camController);

        ModelBuilder modelBuilder = new ModelBuilder();
        model = modelBuilder.createSphere(2f, 2f, 2f, 20, 20,
                new Material(),
                VertexAttributes.Usage.Position |
                        VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);

        for (int x = -5; x <= 5; x+=2) {
            for (int z = -5; z<=5; z+=2) {
                ModelInstance instance = new ModelInstance(model, x, 0, z);
                ColorAttribute attrU = new TestShader.TestColorAttribute(
                        TestShader.TestColorAttribute.DiffuseU, (x+5f)/10f, 1f - (z+5f)/10f, 0, 1);
                instance.materials.get(0).set(attrU);
                ColorAttribute attrV = new TestShader.TestColorAttribute(
                        TestShader.TestColorAttribute.DiffuseV, 1f - (x+5f)/10f, 0, (z+5f)/10f, 1);
                instance.materials.get(0).set(attrV);
                instances.add(instance);
            }
        }

        shader = new TestShader();
        shader.init();

        modelBatch = new ModelBatch();
    }

    @Override
    public void render () {
        camController.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        for (ModelInstance instance : instances)
            modelBatch.render(instance, shader);
        modelBatch.end();
    }

    @Override
    public void dispose () {
        shader.dispose();
        model.dispose();
        modelBatch.dispose();
    }

    @Override
    public void resume () {
    }

    @Override
    public void resize (int width, int height) {
    }

    @Override
    public void pause () {
    }

    private static class TestShader implements Shader {
        ShaderProgram program;
        Camera camera;
        RenderContext context;
        int u_projTrans;
        int u_worldTrans;
        int u_colorU;
        int u_colorV;

        @Override
        public void init() {
            String vert = Gdx.files.internal("data/test.vertex.glsl").readString();
            String frag = Gdx.files.internal("data/test.fragment.glsl").readString();
            program = new ShaderProgram(vert, frag);
            if (!program.isCompiled())
                throw new GdxRuntimeException(program.getLog());
            u_projTrans = program.getUniformLocation("u_projTrans");
            u_worldTrans = program.getUniformLocation("u_worldTrans");
            u_colorU = program.getUniformLocation("u_colorU");
            u_colorV = program.getUniformLocation("u_colorV");
        }

        @Override
        public void dispose() {
            program.dispose();
        }

        @Override
        public void begin(Camera camera, RenderContext context) {
            this.camera = camera;
            this.context = context;
            program.begin();
            program.setUniformMatrix("u_projViewTrans", camera.combined);
            context.setDepthTest(GL20.GL_LEQUAL);
            context.setCullFace(GL20.GL_BACK);
        }

        @Override
        public void render(Renderable renderable) {
            program.setUniformMatrix("u_worldTrans", renderable.worldTransform);
            Color colorU = ((ColorAttribute)renderable.material.get(TestColorAttribute.DiffuseU)).color;
            Color colorV = ((ColorAttribute)renderable.material.get(TestColorAttribute.DiffuseV)).color;
            program.setUniformf(u_colorU, colorU.r, colorU.g, colorU.b);
            program.setUniformf(u_colorV, colorV.r, colorV.g, colorV.b);
            renderable.meshPart.render(program);
        }

        @Override
        public void end() {
            program.end();
        }

        @Override
        public int compareTo(Shader other) {
            return 0;
        }
        @Override
        public boolean canRender(Renderable renderable) {
            return renderable.material.has(TestColorAttribute.DiffuseU | TestColorAttribute.DiffuseV);
        }

        public static class TestColorAttribute extends ColorAttribute {
            public final static String DiffuseUAlias = "diffuseUColor";
            public final static long DiffuseU = register(DiffuseUAlias);

            public final static String DiffuseVAlias = "diffuseVColor";
            public final static long DiffuseV = register(DiffuseVAlias);

            static {
                Mask = Mask | DiffuseU | DiffuseV;
            }

            public TestColorAttribute (long type, float r, float g, float b, float a) {
                super(type, r, g, b, a);
            }
        }
    }
}
