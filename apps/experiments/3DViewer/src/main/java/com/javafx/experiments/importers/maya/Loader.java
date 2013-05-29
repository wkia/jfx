package com.javafx.experiments.importers.maya;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.beans.property.DoubleProperty;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;
import javafx.util.Duration;
import com.javafx.experiments.importers.maya.parser.MParser;
import com.javafx.experiments.importers.maya.values.MArray;
import com.javafx.experiments.importers.maya.values.MBool;
import com.javafx.experiments.importers.maya.values.MCompound;
import com.javafx.experiments.importers.maya.values.MData;
import com.javafx.experiments.importers.maya.values.MFloat;
import com.javafx.experiments.importers.maya.values.MFloat2Array;
import com.javafx.experiments.importers.maya.values.MFloat3;
import com.javafx.experiments.importers.maya.values.MFloat3Array;
import com.javafx.experiments.importers.maya.values.MFloatArray;
import com.javafx.experiments.importers.maya.values.MInt;
import com.javafx.experiments.importers.maya.values.MInt3Array;
import com.javafx.experiments.importers.maya.values.MIntArray;
import com.javafx.experiments.importers.maya.values.MPolyFace;
import com.javafx.experiments.importers.maya.values.MString;
import com.sun.javafx.geom.Vec3f;

/** Loader */
class Loader {
    public static final boolean DEBUG = false;
    public static final boolean WARN = false;

    MEnv env;

    int startFrame;
    int endFrame;

    MNodeType transformType;
    MNodeType jointType;
    MNodeType meshType;
    MNodeType cameraType;
    MNodeType animCurve;
    MNodeType animCurveTA;
    MNodeType animCurveUA;
    MNodeType animCurveUL;
    MNodeType animCurveUT;
    MNodeType animCurveUU;

    MNodeType lambertType;
    MNodeType reflectType;
    MNodeType blinnType;
    MNodeType phongType;
    MNodeType fileType;
    MNodeType skinClusterType;
    MNodeType blendShapeType;
    MNodeType groupPartsType;
    MNodeType shadingEngineType;

    // [Note to Alex]: I've re-enabled joints, but lets not use rootJoint [John]
    // Joint rootJoint; //NO_JOINTS
    Map<MNode, Node> loaded = new HashMap<MNode, Node>();

    Map<Float, List<KeyValue>> keyFrameMap = new TreeMap();

    Map<Node, MNode> meshParents = new HashMap();

    private MFloat3Array mVerts;
    // Optionally force per-face per-vertex normal generation
    private int[] edgeData;

    private List<MData> uvSet;
    private int uvChannel;
    private MFloat3Array mPointTweaks;
    private URL url;

    //=========================================================================
    // Loader.load
    //-------------------------------------------------------------------------
    // Called from MayaImporter.load
    //=========================================================================
    public void load(URL url) {
        this.url = url;
        env = new MEnv();
        MParser parser = new MParser(env);
        try {
            parser.parse(url);
            loadModel();
            for (MNode n : env.getNodes()) {
                // System.out.println("____________________________________________________________");
                // System.out.println("==> .......Node: " + n);
                resolveNode(n);
            }
        } catch (Exception e) {
            if (WARN) System.err.println("Error loading url: [" + url + "]");
            throw new RuntimeException(e);
        }
    }

    //=========================================================================
    // Loader.loadModel
    //=========================================================================
    void loadModel() {
        startFrame = (int) Math.round(env.getPlaybackStart() - 1);
        endFrame = (int) Math.round(env.getPlaybackEnd() - 1);
        transformType = env.findNodeType("transform");
        jointType = env.findNodeType("joint");
        meshType = env.findNodeType("mesh");
        cameraType = env.findNodeType("camera");
        animCurve = env.findNodeType("animCurve");
        animCurveTA = env.findNodeType("animCurveTA");
        animCurveUA = env.findNodeType("animCurveUA");
        animCurveUL = env.findNodeType("animCurveUL");
        animCurveUT = env.findNodeType("animCurveUT");
        animCurveUU = env.findNodeType("animCurveUU");

        lambertType = env.findNodeType("lambert");
        reflectType = env.findNodeType("reflect");
        blinnType = env.findNodeType("blinn");
        phongType = env.findNodeType("phong");
        fileType = env.findNodeType("file");
        skinClusterType = env.findNodeType("skinCluster");
        groupPartsType = env.findNodeType("groupParts");
        shadingEngineType = env.findNodeType("shadingEngine");
        blendShapeType = env.findNodeType("blendShape");
    }

    //=========================================================================
    // Loader.resolveNode
    //-------------------------------------------------------------------------
    // Loader.resolveNode looks up MNode in the HashMap Map<MNode, Node> loaded
    // and returns the Node to which this map maps the MNode.
    // Also, if the node that its looking up hasn't been processed yet,
    // it processes the node.
    //=========================================================================
    Node resolveNode(MNode n) {
        // System.out.println("--> resolveNode: " + n);
        // if the node hasn't already been processed, then process the node
        if (!loaded.containsKey(n)) {
            // System.out.println("--> containsKey: " + n);
            processNode(n);
            // System.out.println("    loaded.get(n) " + loaded.get(n));
        }
        return loaded.get(n);
    }

    //=========================================================================
    // Loader.processNode
    //=========================================================================
    void processNode(MNode n) {
        Group parentNode = null;
        for (MNode p : n.getParentNodes()) {
            parentNode = (Group) resolveNode(p);
        }
        Node result = loaded.get(n);
        // if the result is null, then it hasn't been added to the map yet
        // so go ahead and process it
        if (result == null) {
            if (n.isInstanceOf(shadingEngineType)) {
                //                System.out.println("==> Found a node of shadingEngineType: " + n);
            } else if (n.isInstanceOf(lambertType)) {
                //                System.out.println("==> Found a node of lambertType: " + n);
            } else if (n.isInstanceOf(reflectType)) {
                //                System.out.println("==> Found a node of reflectType: " + n);
            } else if (n.isInstanceOf(blinnType)) {
                //                System.out.println("==> Found a node of blinnType: " + n);
            } else if (n.isInstanceOf(phongType)) {
                //                System.out.println("==> Found a node of phongType: " + n);
            } else if (n.isInstanceOf(fileType)) {
                //                System.out.println("==> Found a node of fileType: " + n);
            } else if (n.isInstanceOf(skinClusterType)) {
                // processClusterType(n);
            } else if (n.isInstanceOf(meshType)) {
                processMeshType(n, parentNode);
            } else if (n.isInstanceOf(jointType)) {
                processJointType(n, parentNode);
            } else if (n.isInstanceOf(transformType)) {
                processTransformType(n, parentNode);
            } else if (n.isInstanceOf(animCurve)) {
                processAnimCurve(n);
            }
        }
    }

    protected void processClusterType(MNode n) {
        loaded.put(n, null);
        MArray ma = (MArray) n.getAttr("ma");
        // NO_JOINTS
        //        List<Joint> jointNodes = new ArrayList<Joint>();
        //        for (int i = 0; i < ma.getSize(); i++) {
        //            // hack...
        //            MNode c = n.getIncomingConnectionToType("ma[" + i + "]", "joint");
        //            Joint jn = (Joint) resolveNode(c);
        //            jointNodes.add(jn);
        ////                   //println("JOINT= {c.getName()} => {jn.id}");
        //        }

        MNode outputMeshMNode = resolveOutputMesh(n);
        MNode inputMeshMNode = resolveInputMesh(n);
        if (inputMeshMNode == null || outputMeshMNode == null) {
            return;
        }
        //               println("ORIG MESH FOR SKIN={origMesh} TARGET={targetMesh}");
        //               MayaMeshNodeGroup targetMayaMeshNode = (MayaMeshNodeGroup) resolveNode(targetMesh);
        MeshView targetMayaMeshNode = (MeshView) resolveNode(outputMeshMNode);
        boolean nn = loaded.containsKey(outputMeshMNode);
        //               println("TARGET MESH {targetMesh} {targetMesh.getNodeType().getName()} {targetMayaMeshNode} n={nn}");
        //               var sourceMayaMeshNode = (resolveNode(origMesh) as MayaMeshNodeGroup);
        MeshView sourceMayaMeshNode = (MeshView) resolveNode(inputMeshMNode);
        // We must be able to find the original converter in the meshConverters map
        MNode origOrigMesh = resolveOrigInputMesh(n);
        //               println("ORIG ORIG={origOrigMesh}");

        // TODO: What is with this? origMesh
        resolveNode(origOrigMesh).setVisible(false);

        MArray bindPreMatrixArray = (MArray) n.getAttr("pm");
        //println("adding SKIN CONTROLLER to {targetMayaMeshNode}");

        Affine[] bindPreMatrix = new Affine[bindPreMatrixArray.getSize()];
        for (int i = 0; i < bindPreMatrixArray.getSize(); i++) {
            bindPreMatrix[i] = convertMatrix((MFloatArray) bindPreMatrixArray.getData(i));
        }

        TriangleMesh sourceMesh = (TriangleMesh) sourceMayaMeshNode.getMesh();
        TriangleMesh targetMesh = (TriangleMesh) targetMayaMeshNode.getMesh();

        MArray mayaWeights = (MArray) n.getAttr("wl");
        //NO_JOINTS
        //        int numJoints = jointNodes.size();
        //
        //        Map<Vertex, Vertex> source2target = new HashMap<Vertex, Vertex>(sourceMesh.getPointCount());
        //
        //        for (int vert = 0; vert < sourceMesh.getPointCount(); vert++) {
        //
        //            MFloatArray curWeights = (MFloatArray) mayaWeights.getData(vert).getData("w");
        //            // NO_JOINTS
        //            List<Double> influences = new ArrayList<Double>();
        //           for (int j = 0; j < numJoints; j++) {
        //               double w = j < curWeights.getSize() ? curWeights.get(j) : 0;
        //               if (w != 0) {
        //                   influences.add((double) j);
        //                   influences.add(w);
        //               }
        //           }
        //           int c = 0;
        //           if (influences.size() > 0) {
        //               //println("influences for {vert} = {for (k in influences) " {k} "}");
        //               c = influences.size() / 2;
        //           }
        //
        //            final int count = c;
        //
        //            Vertex v = sourceMesh.getVertices().get(vert);
        //
        //            if (DEBUG) {
        //                System.out.println("vert=" + vert + " count=" + count);
        //            }
        //            // We create count of joints but only 4 of them will be used
        //            int joints[] = new int[4];
        //            float jointWeights[] = new float[4];
        //            int jointIndex = 0;
        //            for (int j = 0; j < count; j++) {
        //                final int joint = influences.get(2 * j).intValue();
        //                final double weight = influences.get(2 * j + 1);
        //
        //                if (sourceMesh.getJoints().size() == 0) {
        //                    for (int k = 0; k < jointNodes.size(); k++) {
        //                        Joint jointNode = jointNodes.get(k);
        //                        sourceMesh.getJoints().add(new TriangleMesh.Joint(jointNode.getId(),
        //                                bindPreMatrix[k], jointNode.getTransform()));
        //                        jointNode.setInvertedInitialWorldTransform(bindPreMatrix[k]);
        //                    }
        //                }
        //                if (Math.abs(weight) >= 1e-5 && jointIndex < 4) {
        //                    joints[jointIndex] = joint;
        //                    jointWeights[jointIndex] = (float) weight;
        //                    jointIndex++;
        //                }
        //
        //                if (DEBUG) {
        //                    System.out.println("vert=" + vert + " v=" + v + " joint=" + joint + " weight=" + weight);
        //                }
        //            }
        //            Vertex vr = new Vertex(v.x, v.y, v.z,
        //                        joints[0], jointWeights[0],
        //                        joints[1], jointWeights[1],
        //                        joints[2], jointWeights[2],
        //                        joints[3]);
        //            source2target.put(v, vr);
        //        }
        //
        //        for (Vertex v : sourceMesh.getVertices()) {
        //            Vertex tv = source2target.get(v);
        //            if (tv == null) {
        //                tv = v;
        //                source2target.put(v, tv);
        //            }
        //            targetMesh.getVertices().add(tv);
        //        }
        //        targetMesh.getJoints().addAll(sourceMesh.getJoints());// NO_JOINTS
        targetMesh.getPoints().setAll(sourceMesh.getPoints());
        targetMesh.getTexCoords().setAll(sourceMesh.getTexCoords());
        targetMesh.getFaces().setAll(sourceMesh.getFaces());
        targetMesh.getFaceSmoothingGroups().setAll(sourceMesh.getFaceSmoothingGroups());
    }

    protected Image loadImageFromFtnAttr(MNode fileNode, String name) {
        Image image = null;
        MString fileName = (MString) fileNode.getAttr("ftn");
        String imageFilename = (String) fileName.get();
        try {
            File file = new File(imageFilename);
            String filePath;
            if (file.exists()) {
                filePath = file.toURI().toString();
            } else {
                filePath = new URL(url, imageFilename).toString();
            }
            image = new Image(filePath);
            if (DEBUG) {
                System.out.println(name + " = " + filePath);
                System.out.println(name + " w = " + image.getWidth() + " h = " + image.getHeight());
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(MayaImporter.class.getName()).log(Level.SEVERE, "Failed to load " + name + " '" + imageFilename + "'!", ex);
        }
        return image;
    }

    protected void processMeshType(MNode n, Group parentNode) throws RuntimeException {
        //=============================================================
        // When JavaFX supports polygon mesh geometry,
        // add the polygon mesh geometry here.
        // Until then, add a unit square as a placeholder.
        //=============================================================
        Node node = resolveNode(n.getParentNodes().get(0));
        //                if (node != null) {
        //                if (node != null && !n.getName().endsWith("Orig")) {
        // Original approach to mesh placeholder:
        //                     meshParents.put(node, n);

        // Try to find an image or color from n (MNode)
        if (DEBUG) { System.out.println("________________________________________"); }
        if (DEBUG) { System.out.println("n.getName(): " + n.getName()); }
        if (DEBUG) { System.out.println("n.getNodeType(): " + n.getNodeType()); }
        MNode shadingGroup = n.getOutgoingConnectionToType("iog", "shadingEngine", true);
        MNode mat;
        MNode mFile;
        if (DEBUG) { System.out.println("shadingGroup: " + shadingGroup); }

        MFloat3 mColor;
        Vec3f diffuseColor = null;
        Vec3f specularColor = null;

        Image diffuseImage = null;
        Image normalImage = null;
        Image specularImage = null;
        Float specularPower = null;

        if (shadingGroup != null) {
            mat = shadingGroup.getIncomingConnectionToType("ss", "lambert");
            if (mat != null) {
                // shader = shaderMap.get(mat.getName()) as FixedFunctionShader;
                if (DEBUG) { System.out.println("lambert mat: " + mat); }
                mColor = (MFloat3) mat.getAttr("c");
                float diffuseIntensity = ((MFloat) mat.getAttr("dc")).get();
                if (mColor != null) {
                    diffuseColor = new Vec3f(
                            mColor.get()[0] * diffuseIntensity,
                            mColor.get()[1] * diffuseIntensity,
                            mColor.get()[2] * diffuseIntensity);
                    if (DEBUG) { System.out.println("diffuseColor = " + diffuseColor); }
                }

                mFile = mat.getIncomingConnectionToType("c", "file");
                if (mFile != null) {
                    diffuseImage = loadImageFromFtnAttr(mFile, "diffuseImage");
                }
                MNode bump2d = mat.getIncomingConnectionToType("n", "bump2d");
                if (bump2d != null) {
                    mFile = bump2d.getIncomingConnectionToType("bv", "file");
                    if (mFile != null) {
                        normalImage = loadImageFromFtnAttr(mFile, "normalImage");
                    }
                }
            }
            mat = shadingGroup.getIncomingConnectionToType("ss", "phong");
            if (mat != null) {
                // shader = shaderMap.get(mat.getName()) as FixedFunctionShader;
                if (DEBUG) { System.out.println("phong mat: " + mat); }
                mColor = (MFloat3) mat.getAttr("sc");
                if (mColor != null) {
                    specularColor = new Vec3f(
                            mColor.get()[0],
                            mColor.get()[1],
                            mColor.get()[2]);
                    if (DEBUG) { System.out.println("specularColor = " + specularColor); }
                }
                mFile = mat.getIncomingConnectionToType("sc", "file");
                if (mFile != null) {
                    specularImage = loadImageFromFtnAttr(mFile, "specularImage");
                }

                specularPower = ((MFloat) mat.getAttr("cp")).get();
                if (DEBUG) { System.out.println("specularPower = " + specularPower); }
            }
        }

        PhongMaterial material = new PhongMaterial();

        if (diffuseImage != null) {
            material.setDiffuseMap(diffuseImage);
            material.setDiffuseColor(Color.WHITE);
        } else {
            if (diffuseColor != null) {
                material.setDiffuseColor(
                        new Color(
                                diffuseColor.x,
                                diffuseColor.y,
                                diffuseColor.z, 1));
                //                            material.setDiffuseColor(new Color(
                //                                    0.5,
                //                                    0.5,
                //                                    0.5, 0));
            } else {
                material.setDiffuseColor(Color.GRAY);
            }
        }

        if (normalImage != null) {
            material.setBumpMap(normalImage);
        }

        if (specularImage != null) {
            material.setSpecularMap(specularImage);
        } else {
            if (specularColor != null && specularPower != null) {
                material.setSpecularColor(
                        new Color(
                                specularColor.x,
                                specularColor.y,
                                specularColor.z, 1));
                material.setSpecularPower(specularPower / 33);
                //                            material.setSpecularColor(new Color(
                //                                    0,
                //                                    1,
                //                                    0, 1));
                //                            material.setSpecularPower(1);
            } else {
                //                            material.setSpecularColor(new Color(
                //                                    0.2,
                //                                    0.2,
                //                                    0.2, 1));
                //                            material.setSpecularPower(1);
                material.setSpecularColor(null);
            }
        }

        Mesh mesh = convertToFXMesh(n);

        MeshView mv = new MeshView();
        mv.setId(n.getName());
        mv.setMaterial(material);
        //            mv.setWireframe(true);
        //            mv.setAmbient(Color.GRAY); // TODO???
        mv.setMesh(mesh);

        loaded.put(n, mv);
        if (node != null) {
            ((Group) node).getChildren().add(mv);
        }
    }

    protected void processJointType(MNode n, Group parentNode) {
        // [Note to Alex]: I've re-enabled joints, but not skinning yet [John]
        Node result;
        MFloat3 t = (MFloat3) n.getAttr("t");
        MFloat3 jo = (MFloat3) n.getAttr("jo");
        MFloat3 r = (MFloat3) n.getAttr("r");
        MFloat3 s = (MFloat3) n.getAttr("s");
        String id = n.getName();

        Joint j = new Joint();
        j.setId(id);

        // There's various ways to get the same thing:
        // n.getAttr("r").get()[0]
        // n.getAttr("r").getX()
        // n.getAttr("rx")
        // Up to you which you prefer

        j.t.setX(t.get()[0]);
        j.t.setY(t.get()[1]);
        j.t.setZ(t.get()[2]);

        // if ssc (Segment Scale Compensate) is false, then it is = 1, 1, 1
        boolean ssc = ((MBool) n.getAttr("ssc")).get();
        if (ssc) {
            List<MNode> parents = n.getParentNodes();
            if (parents.size() > 0) {
                MFloat3 parent_s = (MFloat3) n.getParentNodes().get(0).getAttr("s");
                j.is.setX(1f / parent_s.getX());
                j.is.setY(1f / parent_s.getY());
                j.is.setZ(1f / parent_s.getZ());
            } else {
                j.is.setX(1f);
                j.is.setY(1f);
                j.is.setZ(1f);
            }
        } else {
            j.is.setX(1f);
            j.is.setY(1f);
            j.is.setZ(1f);
        }

        /*
        // This code doesn't seem to work right:
        MFloat jox = (MFloat) n.getAttr("jox");
        MFloat joy = (MFloat) n.getAttr("joy");
        MFloat joz = (MFloat) n.getAttr("joz");
        j.jox.setAngle(jox.get());
        j.joy.setAngle(joy.get());
        j.joz.setAngle(joz.get());
        // The following code works right:
        */

        if (jo != null) {
            j.jox.setAngle(jo.getX());
            j.joy.setAngle(jo.getY());
            j.joz.setAngle(jo.getZ());
        } else {
            j.jox.setAngle(0f);
            j.joy.setAngle(0f);
            j.joz.setAngle(0f);
        }

        MFloat rx = (MFloat) n.getAttr("rx");
        MFloat ry = (MFloat) n.getAttr("ry");
        MFloat rz = (MFloat) n.getAttr("rz");
        j.rx.setAngle(rx.get());
        j.ry.setAngle(ry.get());
        j.rz.setAngle(rz.get());

        j.s.setX(s.get()[0]);
        j.s.setY(s.get()[1]);
        j.s.setZ(s.get()[2]);

        result = j;
        // Add the Joint to the map
        loaded.put(n, j);
        j.setDepthTest(DepthTest.ENABLE);
        // Add the Joint to its JavaFX parent
        if (parentNode != null) {
            parentNode.getChildren().add(j);
            if (DEBUG) System.out.println("j.getDepthTest() : " + j.getDepthTest());
        }
        if (parentNode == null || !(parentNode instanceof Joint)) {
            // [Note to Alex]: I've re-enabled joints, but lets not use rootJoint [John]
            // rootJoint = j;
        }
    }

    protected void processTransformType(MNode n, Group parentNode) {
        MFloat3 t = (MFloat3) n.getAttr("t");
        MFloat3 r = (MFloat3) n.getAttr("r");
        MFloat3 s = (MFloat3) n.getAttr("s");
        String id = n.getName();
        // ignore cameras
        if ("persp".equals(id) ||
                "top".equals(id) ||
                "front".equals(id) ||
                "side".equals(id)) {
            return;
        }

        MayaGroup mGroup = new MayaGroup();
        mGroup.setId(n.getName());
        // g.setBlendMode(BlendMode.SRC_OVER);

        // if (DEBUG) System.out.println("t = " + t);
        // if (DEBUG) System.out.println("r = " + r);
        // if (DEBUG) System.out.println("s = " + s);

        mGroup.t.setX(t.get()[0]);
        mGroup.t.setY(t.get()[1]);
        mGroup.t.setZ(t.get()[2]);

        MFloat rx = (MFloat) n.getAttr("rx");
        MFloat ry = (MFloat) n.getAttr("ry");
        MFloat rz = (MFloat) n.getAttr("rz");
        mGroup.rx.setAngle(rx.get());
        mGroup.ry.setAngle(ry.get());
        mGroup.rz.setAngle(rz.get());

        mGroup.s.setX(s.get()[0]);
        mGroup.s.setY(s.get()[1]);
        mGroup.s.setZ(s.get()[2]);

        MFloat rptx = (MFloat) n.getAttr("rptx");
        MFloat rpty = (MFloat) n.getAttr("rpty");
        MFloat rptz = (MFloat) n.getAttr("rptz");
        mGroup.rpt.setX(rptx.get());
        mGroup.rpt.setY(rpty.get());
        mGroup.rpt.setZ(rptz.get());

        MFloat rpx = (MFloat) n.getAttr("rpx");
        MFloat rpy = (MFloat) n.getAttr("rpy");
        MFloat rpz = (MFloat) n.getAttr("rpz");
        mGroup.rp.setX(rpx.get());
        mGroup.rp.setY(rpy.get());
        mGroup.rp.setZ(rpz.get());

        mGroup.rpi.setX(-rpx.get());
        mGroup.rpi.setY(-rpy.get());
        mGroup.rpi.setZ(-rpz.get());

        MFloat spx = (MFloat) n.getAttr("spx");
        MFloat spy = (MFloat) n.getAttr("spy");
        MFloat spz = (MFloat) n.getAttr("spz");
        mGroup.sp.setX(spx.get());
        mGroup.sp.setY(spy.get());
        mGroup.sp.setZ(spz.get());

        mGroup.spi.setX(-spx.get());
        mGroup.spi.setY(-spy.get());
        mGroup.spi.setZ(-spz.get());

        // Add the MayaGroup to the map
        loaded.put(n, mGroup);
        // Add the MayaGroup to its JavaFX parent
        if (parentNode != null) {
            parentNode.getChildren().add(mGroup);
        }
    }

    protected void processAnimCurve(MNode n) {
        // if (DEBUG) System.out.println("processing anim curve");
        List<MPath> toPaths = n.getPathsConnectingFrom("o");
        loaded.put(n, null);
        for (MPath path : toPaths) {
            MNode toNode = path.getTargetNode();
            // if (DEBUG) System.out.println("toNode = "+ toNode.getNodeType());
            if (toNode.isInstanceOf(transformType)) {
                Node to = resolveNode(toNode);
                if (to instanceof MayaGroup) {
                    MayaGroup g = (MayaGroup) to;
                    DoubleProperty ref = null;
                    String s = path.getComponentSelector();
                    // if (DEBUG) System.out.println("selector = " + s);
                    if ("t[0]".equals(s)) {
                        ref = g.t.xProperty();
                    } else if ("t[1]".equals(s)) {
                        ref = g.t.yProperty();
                    } else if ("t[2]".equals(s)) {
                        ref = g.t.zProperty();
                    } else if ("s[0]".equals(s)) {
                        ref = g.s.xProperty();
                    } else if ("s[1]".equals(s)) {
                        ref = g.s.yProperty();
                    } else if ("s[2]".equals(s)) {
                        ref = g.s.zProperty();
                    } else if ("r[0]".equals(s)) {
                        ref = g.rx.angleProperty();
                    } else if ("r[1]".equals(s)) {
                        ref = g.ry.angleProperty();
                    } else if ("r[2]".equals(s)) {
                        ref = g.rz.angleProperty();
                    } else if ("rp[0]".equals(s)) {
                        ref = g.rp.xProperty();
                    } else if ("rp[1]".equals(s)) {
                        ref = g.rp.yProperty();
                    } else if ("rp[2]".equals(s)) {
                        ref = g.rp.zProperty();
                    } else if ("sp[0]".equals(s)) {
                        ref = g.sp.xProperty();
                    } else if ("sp[1]".equals(s)) {
                        ref = g.sp.yProperty();
                    } else if ("sp[2]".equals(s)) {
                        ref = g.sp.zProperty();
                    }
                    // Note: may also want to consider adding rpt in addition to rp and sp
                    if (ref != null) {
                        convertAnimCurveRange(n, ref, true);
                    }
                }
                // [Note to Alex]: I've re-enabled joints, but not skinning yet [John]
                if (to instanceof Joint) {
                    Joint j = (Joint) to;
                    DoubleProperty ref = null;
                    String s = path.getComponentSelector();
                    // if (DEBUG) System.out.println("selector = " + s);
                    if ("t[0]".equals(s)) {
                        ref = j.t.xProperty();
                    } else if ("t[1]".equals(s)) {
                        ref = j.t.yProperty();
                    } else if ("t[2]".equals(s)) {
                        ref = j.t.zProperty();
                    } else if ("s[0]".equals(s)) {
                        ref = j.s.xProperty();
                    } else if ("s[1]".equals(s)) {
                        ref = j.s.yProperty();
                    } else if ("s[2]".equals(s)) {
                        ref = j.s.zProperty();
                    } else if ("jo[0]".equals(s)) {
                        ref = j.jox.angleProperty();
                    } else if ("jo[1]".equals(s)) {
                        ref = j.joy.angleProperty();
                    } else if ("jo[2]".equals(s)) {
                        ref = j.joz.angleProperty();
                    } else if ("r[0]".equals(s)) {
                        ref = j.rx.angleProperty();
                    } else if ("r[1]".equals(s)) {
                        ref = j.ry.angleProperty();
                    } else if ("r[2]".equals(s)) {
                        ref = j.rz.angleProperty();
                    }
                    if (ref != null) {
                        convertAnimCurveRange(n, ref, true);
                    }
                }
                break;
            }
        }
    }

    private Mesh convertToFXMesh(MNode n) {
        mVerts = (MFloat3Array) n.getAttr("vt");
        MPolyFace mPolys = (MPolyFace) n.getAttr("fc");
        mPointTweaks = (MFloat3Array) n.getAttr("pt");
        MInt3Array mEdges = (MInt3Array) n.getAttr("ed");
        edgeData = mEdges.get();
        uvSet = ((MArray) n.getAttr("uvst")).get();
        String currentUVSet = ((MString) n.getAttr("cuvs")).get();
        for (int i = 0; i < uvSet.size(); i++) {
            if (((MString) uvSet.get(i).getData("uvsn")).get().equals(currentUVSet)) {
                uvChannel = i;
            }
        }

        if (mPolys.getFaces() == null) {
            return new TriangleMesh();
        }

        MFloat3Array normals = (MFloat3Array) n.getAttr("n");
        return buildMeshData(mPolys.getFaces(), normals);
    }

    private int edgeVert(int edgeNumber, boolean start) {
        boolean reverse = (edgeNumber < 0);
        if (reverse) {
            edgeNumber = reverse(edgeNumber);
            return edgeData[3 * edgeNumber + (start ? 1 : 0)];
        } else {
            return edgeData[3 * edgeNumber + (start ? 0 : 1)];
        }
    }

    private int reverse(int edge) {
        if (edge < 0) {
            return -edge - 1;
        }
        return edge;
    }

    private boolean edgeIsSmooth(int edgeNumber) {
        edgeNumber = reverse(edgeNumber);
        return edgeData[3 * edgeNumber + 2] != 0;
    }

    private int edgeStart(int edgeNumber) {
        return edgeVert(edgeNumber, true);
    }

    private int edgeEnd(int edgeNumber) {
        return edgeVert(edgeNumber, false);
    }

    private boolean addUVs(TriangleMesh mesh, int uvChannel) {
        if (uvSet == null || uvChannel < 0 || uvChannel >= uvSet.size()) {
            return false;
        }
        MCompound compound = (MCompound) uvSet.get(uvChannel);
        MFloat2Array uvs = (MFloat2Array) compound.getFieldData("uvsp");
        if (uvs == null || uvs.get() == null) {
            return false;
        }

        float[] res = new float[uvs.getSize() * 2];
        float[] uvsData = uvs.get();
        for (int i = 0; i < uvs.getSize(); i++) {
            //note the 1 - v
            res[i * 2] = uvsData[2 * i];
            res[i * 2 + 1] = 1 - uvsData[2 * i + 1];
        }
        mesh.getTexCoords().setAll(res);
        return true;
    }

    private void getVert(int index, Vec3f vert) {
        float[] verts = mVerts.get();
        float[] tweaks = null;
        if (mPointTweaks != null) {
            tweaks = mPointTweaks.get();
            if (tweaks != null) {
                if ((3 * index + 2) >= tweaks.length) {
                    tweaks = null;
                }
            }
        }
        if (tweaks == null) {
            vert.set(verts[3 * index + 0], verts[3 * index + 1], verts[3 * index + 2]);
        } else {
            vert.set(
                    verts[3 * index + 0] + tweaks[3 * index + 0],
                    verts[3 * index + 1] + tweaks[3 * index + 1],
                    verts[3 * index + 2] + tweaks[3 * index + 2]);
        }
    }

    float FPS = 24.0f;
    float TAN_FIXED = 1;
    float TAN_LINEAR = 2;
    float TAN_FLAT = 3;
    float TAN_STEPPED = 5;
    float TAN_SPLINE = 9;
    float TAN_CLAMPED = 10;
    float TAN_PLATEAU = 16;

    // Experimentally trying to land the frames on whole frame values
    // Duration is still double, but internally, in Animation/Timeline,
    // the time is discrete.  6000 units per second.
    // Without this EPSILON, the frames might not land on whole frame values.
    // 0.000001f seems to work for now
    // 0.0000001f was too small on a trial run
    static final float EPSILON = 0.000001f;

    static final float MAXIMUM = 10000000.0f;

    // Empirically derived from playing with animation curve editor
    float TAN_EPSILON = 0.05f;

    //=========================================================================
    // Loader.convertAnimCurveRange
    //-------------------------------------------------------------------------
    // This method adds to keyFrameMap which is a
    // TreeMap Map<Float, List<KeyValue>>
    //=========================================================================
    void convertAnimCurveRange(
            MNode n, final DoubleProperty property,
            boolean convertAnglesToDegrees) {
        Collection inputs = n.getConnectionsTo("i");
        boolean isDrivenAnimCurve = (inputs.size() > 0);
        boolean useTangentInterpolator = true;  // use the NEW tangent interpolator

        //---------------------------------------------------------------------
        // Tangent types we need to handle:
        //   2 = Linear
        //       - The in/out tangent points in the direction of the previous/next key
        //   3 = Flat
        //       - The in/out tangent has no y component
        //   5 = Stepped
        //       - If this is seen on the out tangent of the previous
        //         frame, immediately goes to the next value
        //   9 = Spline
        //       - The in / out tangents around the current keyframe
        //         match the slope defined by the previous and next
        //         keyframes.
        //  10 = Clamped
        //       - Uses spline tangents unless the keyframe is very close to the next or
        //         previous value, in which case it uses linear tangents.
        //  16 = Plateau
        //       - Generally speaking, if the keyframe is a local maximum or minimum,
        //         uses flat tangents to prevent the curve from overshooting the keyframe.
        //         Seems to use spline tangents when the keyframe is not a local extremum.
        //         There is an epsilon factor built in when deciding whether the flattening
        //         behavior is to be applied.
        // Tangent types we aren't handling:
        //   1 = Fixed
        //  17 = StepNext
        //---------------------------------------------------------------------

        MArray ktv = (MArray) n.getAttr("ktv");
        MInt tan = (MInt) n.getAttr("tan");
        int len = ktv.getSize();

        // Note: the kix, kiy, kox, koy from Maya
        // are most likely unit vectors [kix, kiy] and [kox, koy]
        // in some tricky units that Ken figured out.
        MFloatArray kix = (MFloatArray) n.getAttr("kix");
        MFloatArray kiy = (MFloatArray) n.getAttr("kiy");
        MFloatArray kox = (MFloatArray) n.getAttr("kox");
        MFloatArray koy = (MFloatArray) n.getAttr("koy");
        MIntArray kit = (MIntArray) n.getAttr("kit");
        MIntArray kot = (MIntArray) n.getAttr("kot");
        boolean hasTangent = kix != null && kix.get() != null && kix.get().length > 0;
        boolean isRotation = n.isInstanceOf(animCurveTA) || n.isInstanceOf(animCurveUA);
        boolean keyTimesInSeconds =
                (n.isInstanceOf(animCurveUA) || n.isInstanceOf(animCurveUL) ||
                        n.isInstanceOf(animCurveUT) || n.isInstanceOf(animCurveUU));

        List<KeyFrame> drivenKeys = new LinkedList();

        // Many incoming animation curves start at keyframe 1; to
        // correctly interpret these we need to subtract off one frame
        // from each key time
        boolean needsOneFrameAdjustment = false;

        // For computing tangents around the current point
        float[] keyTimes = new float[3];
        float[] keyValues = new float[3];
        boolean[] keysValid = new boolean[3];
        float[] prevOutTan = new float[3];  // for orig interpolator
        float[] curOutTan = new float[3];  // for tan interpolator
        float[] curInTan = new float[3];  // for both interpolators
        Collection toPaths = n.getPathsConnectingFrom("o");
        String keyName = null;
        String targetName = null;
        for (Object obj : toPaths) {
            MPath toPath = (MPath) obj;
            keyName = toPath.getComponentSelector();
            targetName = toPath.getTargetNode().getName();
        }

        for (int j = 0; j < len; j++) {
            MCompound k1 = (MCompound) ktv.getData(j);

            float kt = ((MFloat) k1.getData("kt")).get();
            float kv = ((MFloat) k1.getData("kv")).get();
            if (j == 0 && !keyTimesInSeconds) {
                needsOneFrameAdjustment = (kt != 0.0f);
                //                if (DEBUG) System.out.println("needsOneFrameAdjustment = " + needsOneFrameAdjustment);
            }

            //------------------------------------------------------------
            // Find out the previous times, values, and durations,
            // if they exist
            // (this code is both for tan interpolator and orig interpolator)
            // Ken's duration is now called durationPrev
            // Ken's k0 is now called kPrev
            //------------------------------------------------------------
            float durationPrev = 0.0f;
            float ktPrev = 0.0f;
            float kvPrev = 0.0f;
            if (j > 0) {
                MCompound kPrev = (MCompound) ktv.getData(j - 1);
                ktPrev = ((MFloat) kPrev.getData("kt")).get();
                kvPrev = ((MFloat) kPrev.getData("kv")).get();  // NEW
                durationPrev = kt - ktPrev;
            }

            //------------------------------------------------------------
            // Find out the next times, values, and durations,
            // if they exist
            // (this code is specifically for TangentInterpolator)
            //------------------------------------------------------------
            float durationNext = 0.0f;
            float ktNext = 0.0f;
            float kvNext = 0.0f;
            if ((j + 1) < len) {
                MCompound kNext = (MCompound) ktv.getData(j + 1);
                ktNext = ((MFloat) kNext.getData("kt")).get();
                kvNext = ((MFloat) kNext.getData("kv")).get();  // NEW
                durationNext = ktNext - kt;
            }

            if (!keyTimesInSeconds) {
                // convert frames to seconds
                kt /= FPS;
                ktPrev /= FPS;  // NEW
                ktNext /= FPS;  // NEW
            } else {
                // convert seconds to frames
                durationPrev *= FPS;
                durationNext *= FPS;  // NEW
            }
            /*
              var ktd = kt;
              if (range != null) {
              if (range.start > ktd or range.end < ktd) {
              continue;
              }
              }
            */


            // Determine the tangent types on both sides
            int prevOutTanType = tan.get();  // for orig interpolator
            int curInTanType = tan.get();  // for both interpolators
            int curOutTanType = tan.get();  // for tan intepolator
            if (j > 0 && j < kot.getSize()) {
                int tmp = kot.get(j - 1);
                // Will be 0 if not actually written in the file
                if (tmp != 0) {
                    prevOutTanType = tmp;
                }
            }
            if (j < kot.getSize()) {  // NEW
                int tmp = kot.get(j);
                if (tmp != 0) {
                    curOutTanType = tmp;
                }
            }
            if (j < kit.getSize()) {
                int tmp = kit.get(j);
                if (tmp != 0) {
                    curInTanType = tmp;
                }
            }

            // Get previous out tangent
            getTangent(
                    ktv, kix, kiy, kox, koy,
                    j - 1,
                    prevOutTanType,
                    false,
                    isRotation,
                    keyTimesInSeconds,
                    prevOutTan,
                    // Temporaries
                    keyTimes, keyValues, keysValid);

            // NEW
            // for tangentInterpolator, we also need curOutTangent
            // Get current out tangent
            getTangent(
                    ktv, kix, kiy, kox, koy,
                    j,
                    curOutTanType,
                    false,
                    isRotation,
                    keyTimesInSeconds,
                    curOutTan,
                    // Temporaries
                    keyTimes, keyValues, keysValid);

            // Get current in tangent
            getTangent(
                    ktv, kix, kiy, kox, koy,
                    j,
                    curInTanType,
                    true,
                    isRotation,
                    keyTimesInSeconds,
                    curInTan,
                    // Temporaries
                    keyTimes, keyValues, keysValid);

            // Create the appropriate interpolator type:
            // [*] DISCRETE for STEPPED type for prevOutTanType
            // [*] Interpolator.TANGENT
            // [*] custom Maya animation curve interpolator if specified
            Interpolator interp = Interpolator.DISCRETE;
            if (prevOutTanType == TAN_STEPPED) {
                // interp = DISCRETE;
            } else {
                if (useTangentInterpolator) {
                    //--------------------------------------------------
                    // TangentIntepolator
                    double k_ix = curInTan[0];
                    double k_iy = curInTan[1];
                    // don't use prevOutTan for tangentInterpolator
                    // double k_ox = prevOutTan[0];
                    // double k_oy = prevOutTan[1];
                    double k_ox = curOutTan[0];
                    double k_oy = curOutTan[1];

                    /*
                      if (DEBUG) System.out.println("n.getName(): " + n.getName());
                      if (DEBUG) System.out.println("(k_ix = " + k_ix + ", " +
                      "k_iy = " + k_iy + ", " +
                      "k_ox = " + k_ox + ", " +
                      "k_oy = " + k_oy + ")"
                      );
                    */

                    // if (DEBUG) System.out.println("FPS = " + FPS);

                    double inTangent = 0.0;
                    double outTangent = 0.0;

                    // Compute the in tangent
                    if (k_ix != 0) {
                        inTangent = k_iy / (k_ix * FPS);
                    }
                    // Compute the out tangent
                    if (k_ox != 0) {
                        outTangent = k_oy / (k_ox * FPS);
                    }

                    // Compute 1/3 of the time interval of this keyframe
                    double oneThirdDeltaPrev = durationPrev / 3.0f;
                    double oneThirdDeltaNext = durationNext / 3.0f;

                    // Note: for angular animation curves, the tangents encode
                    // changes in radians rather than degrees. Now that our
                    // animation curves also emit radians, no conversion is
                    // necessary here.
                    double inTangentValue = -1 * inTangent * oneThirdDeltaPrev + kv;
                    double outTangentValue = outTangent * oneThirdDeltaNext + kv;
                    // We need to add "+ kv", because the value for the tangent
                    // interpolator is in "world space" and not relative to the key

                    if (inTangentValue > MAXIMUM) {
                        inTangentValue = MAXIMUM;
                    }
                    if (outTangentValue > MAXIMUM) {
                        outTangentValue = MAXIMUM;
                    }

                    double timeDeltaPrev = (durationPrev / FPS) * 1000f / 3.0f;  // in ms
                    double timeDeltaNext = (durationNext / FPS) * 1000f / 3.0f;  // in ms

                    if (true) {
                        //                        if (DEBUG) System.out.println("________________________________________");
                        //                        if (DEBUG) System.out.println("n.getName() = " + n.getName());
                        //                        if (DEBUG) System.out.println("kv = " + kv);
                        //                        if (DEBUG) System.out.println("Interpolator.TANGENT(" +
                        //                                           "Duration.valueOf(" +
                        //                                           timeDeltaPrev + ")" + ", " +
                        //                                           inTangentValue + ", " +
                        //                                           "Duration.valueOf(" +
                        //                                           timeDeltaNext + ")" + ", " +
                        //                                           outTangentValue + ");"
                        //                                           );

                    }

                    //--------------------------------------------------
                    // Given the diagram below, where
                    //     k = keyframe
                    //     i = inTangent
                    //     o = outTangent
                    //     + = timeDelta
                    // Its extremely important to note that
                    // inTangent's and outTangent's values for "i" and "o"
                    // are NOT relative to "k".  They are in "worldSpace".
                    // However, the timeDeltaNext and timeDeltaPrev
                    // are in fact relative to the keyframe "k",
                    // and are always an absolute value.
                    // So, in summary,
                    // the Y-axis values are not relative, but
                    // the X-axis values are relative, and always positive
                    //--------------------------------------------------
                    // (Y-axis worldSpace value for i)
                    //    inTangent i
                    //              |
                    //              |        timeDeltaNext (relative to x)
                    //              |         |<------->|
                    //              +---------k---------+
                    //              |<------->|         |
                    //             timeDeltaPrev        |
                    //                                  |
                    //                                  o outTangent
                    //                  (Y-axis worldSpace value for o)
                    //--------------------------------------------------
                    Duration inDuration = Duration.millis(timeDeltaPrev);
                    if (inDuration.toMillis() == 0) {
                        interp = Interpolator.TANGENT(Duration.millis(timeDeltaNext), outTangentValue);
                    } else {
                        interp = Interpolator.TANGENT(
                                inDuration, inTangentValue,
                                Duration.millis(timeDeltaNext), outTangentValue);
                    }
                } else {
                    MayaAnimationCurveInterpolator mayaInterp =
                            createMayaAnimationCurveInterpolator(
                                    prevOutTan[0], prevOutTan[1],
                                    curInTan[0], curInTan[1],
                                    durationPrev,
                                    true);
                    // mayaInterp.isRotation = isRotation;  // was commented out long ago by Ken/Chris
                    // mayaInterp.debug = targetName + "." + keyName + "@"+ kt;
                    interp = mayaInterp;
                }
            }

            float t = kt - EPSILON;
            if (t < 0.0) {
                t = 0.0f;
            }

            /*
            // This was the old way of adjusting
            // for the one frame adjustment.
            if (needsOneFrameAdjustment) {
                t = kt - 1.0f/FPS;
            } else {
                t = kt;
            }
            // The new way is below ...
            // See: (needsOneFrameAdjustment && (j == 0))
            */

            // if (DEBUG) System.out.println("j = " + j);
            //            if (DEBUG) System.out.println("t = " + t);
            if (isRotation) {
                // Maya angular animation curves implicitly output in radians.
                // In order to properly process them throughout the utility node
                // network, we have to follow this convention, and implicitly
                // convert the inputs of transforms' rotation angles to degrees
                // at the end.
                if (!convertAnglesToDegrees) {
                    kv = (float) Math.toRadians(kv);
                }
            }
            // if (DEBUG) System.out.println("creating key value at: " + t + ": " + targetName + "." + keyName);
            KeyValue keyValue = new KeyValue(property, kv, interp);  // [!] API change

            // If the first frame is at frame 1,
            // at least for now, try adding in a frame at frame 0
            // which is a duplicate of the frame at frame 1,
            // to counter-act some strange behavior we are seeing
            // if there is no key at frame 0.
            if (needsOneFrameAdjustment && (j == 0)) {
                if (DEBUG) System.out.println("[!] ATTEMPTING FRAME ONE ADJUSTMENT [!]");
                // [!] API change
                // KeyValue keyValue0 = new KeyValue(property, kv, Interpolator.LINEAR);
                KeyValue keyValue0 = new KeyValue(property, kv);
                addKeyframe(0.0f, keyValue0);
            }

            // Add keyframe
            addKeyframe(t, keyValue);

            /*
            // If you're at the last keyframe,
            // at least for now, try adding in an extra frame
            // to pad the ending
            if (j == (len - 1)) {
                addKeyframe((t+0.0001667f), keyValue);
            }
            */
        }
    }

    //=========================================================================
    // Loader.addKeyframe
    //=========================================================================
    void addKeyframe(float t, KeyValue keyValue) {
        List<KeyValue> vals = keyFrameMap.get(t);
        if (vals == null) {
            vals = new LinkedList<KeyValue>();
            keyFrameMap.put(t, vals);
        }
        vals.add(keyValue);
    }

    //=========================================================================
    // Loader.createMayaAnimationCurveInterpolator
    //=========================================================================
    MayaAnimationCurveInterpolator createMayaAnimationCurveInterpolator(
            float kox,
            float koy,
            float kix,
            float kiy,
            float duration,
            boolean hasTangent) {
        if (duration == 0.0f) {
            return new MayaAnimationCurveInterpolator(0, 0, true);
        } else {
            // Compute the out tangent
            float outTangent = koy / (kox * FPS);
            // Compute the in tangent
            float inTangent = kiy / (kix * FPS);
            // Compute 1/3 of the time interval of this keyframe
            float oneThirdDelta = duration / 3.0f;

            // Note: for angular animation curves, the tangents encode
            // changes in radians rather than degrees. Now that our
            // animation curves also emit radians, no conversion is
            // necessary here.
            float p1Delta = outTangent * oneThirdDelta;
            float p2Delta = -inTangent * oneThirdDelta;
            return new MayaAnimationCurveInterpolator(p1Delta, p2Delta, false);
        }
    }

    //=========================================================================
    // Loader.getTangent
    //=========================================================================
    void getTangent(
            MArray ktv,
            MFloatArray kix,
            MFloatArray kiy,
            MFloatArray kox,
            MFloatArray koy,
            int index,
            int tangentType,
            boolean inTangent,
            boolean isRotation,
            boolean keyTimesInSeconds,
            float[] result,
            // Temporaries
            float[] tmpKeyTimes,
            float[] tmpKeyValues,
            boolean[] tmpKeysValid) {
        float[] output = result;
        float[] keyTimes = tmpKeyTimes;
        float[] keyValues = tmpKeyValues;
        boolean[] keysValid = tmpKeysValid;
        if (inTangent) {
            if (index >= 0 && index < kix.getSize() && index < kiy.getSize()) {
                output[0] = kix.get(index);
                output[1] = kiy.get(index);
                if (output[0] != 0.0f ||
                        output[1] != 0.0f) {
                    // A keyframe was specified in the file
                    return;
                }
            }
        } else {
            if (index >= 0 && index < kox.getSize() && index < koy.getSize()) {
                output[0] = kox.get(index);
                output[1] = koy.get(index);
                if (output[0] != 0.0f ||
                        output[1] != 0.0f) {
                    // A keyframe was specified in the file
                    return;
                }
            }
        }

        // Need to compute the tangent from the surrounding key times and values
        int i = -1;
        while (i < 2) {
            int cur = index + i;
            if (cur >= 0 && cur < ktv.getSize()) {
                MCompound k1 = (MCompound) ktv.getData(cur);
                float kt = ((MFloat) k1.getData("kt")).get();
                if (keyTimesInSeconds) {
                    // Convert seconds to frames
                    kt *= FPS;
                }
                float kv = ((MFloat) k1.getData("kv")).get();
                if (isRotation) {
                    // Maya angular animation curves implicitly output in radians -- see below
                    kv = (float) Math.toRadians(kv);
                }
                keyTimes[1 + i] = kt;
                keyValues[1 + i] = kv;
                keysValid[1 + i] = true;
            } else {
                keysValid[1 + i] = false;
            }
            ++i;
        }
        computeTangent(keyTimes, keyValues, keysValid, tangentType, inTangent, result);
    }

    //=========================================================================
    // Loader.computeTangent
    //=========================================================================
    void computeTangent(
            float[] keyTimes,
            float[] keyValues,
            boolean[] keysValid,
            float tangentType,
            boolean inTangent,
            float[] computedTangent) {
        float[] output = computedTangent;
        if (tangentType == TAN_LINEAR) {
            float x0;
            float x1;
            float y0;
            float y1;
            if (inTangent) {
                if (!keysValid[0]) {
                    // Start of the animation curve: doesn't matter
                    output[0] = 1.0f;
                    output[1] = 0.0f;
                    return;
                }
                x0 = keyTimes[0];
                x1 = keyTimes[1];
                y0 = keyValues[0];
                y1 = keyValues[1];
            } else {
                if (!keysValid[2]) {
                    // End of the animation curve: doesn't matter
                    output[0] = 1.0f;
                    output[1] = 0.0f;
                    return;
                }
                x0 = keyTimes[1];
                x1 = keyTimes[2];
                y0 = keyValues[1];
                y1 = keyValues[2];
            }
            float dx = x1 - x0;
            float dy = y1 - y0;
            output[0] = dx;
            output[1] = dy;
            // Fall through to perform normalization
        } else if (tangentType == TAN_FLAT) {
            output[0] = 1.0f;
            output[1] = 0.0f;
            return;
        } else if (tangentType == TAN_STEPPED) {
            // Doesn't matter what the tangent values are -- will use discrete type interpolator
            return;
        } else if (tangentType == TAN_SPLINE) {
            // Whether we're computing the in or out tangent, if we don't have one or the other
            // keyframe, it reduces to a simpler case
            if (!(keysValid[0] && keysValid[2])) {
                // Reduces to the linear case
                computeTangent(keyTimes, keyValues, keysValid, TAN_LINEAR, inTangent, computedTangent);
                return;
            }

            // Figure out the slope between the adjacent keyframes
            output[0] = keyTimes[2] - keyTimes[0];
            output[1] = keyValues[2] - keyValues[0];
        } else if (tangentType == TAN_CLAMPED) {
            if (!(keysValid[0] && keysValid[2])) {
                // Reduces to the linear case at the ends of the animation curve
                computeTangent(keyTimes, keyValues, keysValid, TAN_LINEAR, inTangent, computedTangent);
                return;
            }

            float inDiff = Math.abs(keyValues[1] - keyValues[0]);
            float outDiff = Math.abs(keyValues[2] - keyValues[1]);

            if (inDiff <= TAN_EPSILON || outDiff <= TAN_EPSILON) {
                // The Maya docs say that this reduces to the linear
                // case. If this were true, then the apparent behavior
                // would be to compute the linear tangent between the
                // two keyframes which are closest together, and
                // reflect that tangent about the current keyframe.
                // computeTangent(keyTimes, keyValues, keysValid, TAN_LINEAR, (inDiff < outDiff), computedTangent);

                // However, experimentation in the curve editor
                // clearly indicates for our test cases that flat
                // rather than linear interpolation is used in this
                // case. Therefore to match Maya's actual behavior
                // more closely we do the following.
                computeTangent(keyTimes, keyValues, keysValid, TAN_FLAT, inTangent, computedTangent);
            } else {
                // Use spline tangents
                computeTangent(keyTimes, keyValues, keysValid, TAN_SPLINE, inTangent, computedTangent);
            }

            return;
        } else if (tangentType == TAN_PLATEAU) {
            if (!(keysValid[0] && keysValid[2])) {
                // Reduces to the flat case at the ends of the animation curve
                computeTangent(keyTimes, keyValues, keysValid, TAN_FLAT, inTangent, computedTangent);
                return;
            }

            // Otherwise, figure out whether we have any local extremum
            if ((keyValues[1] > keyValues[0] &&
                    keyValues[1] > keyValues[2]) ||
                    (keyValues[1] < keyValues[0] &&
                            keyValues[1] < keyValues[2])) {
                // Use flat tangent
                computeTangent(keyTimes, keyValues, keysValid, TAN_FLAT, inTangent, computedTangent);
            } else {
                // The rule is that we use spline tangents unless
                // doing so would cause the curve to go outside the
                // envelope of the keyvalues. To figure this out, we
                // have to compute both the in and out tangents as
                // though we were using splines, and see whether the
                // intermediate bezier control points go outside the
                // hull.
                //
                // Note that it doesn't matter whether we compute the
                // "in" or "out" tangent at the current point -- the
                // result is the same.
                computeTangent(keyTimes, keyValues, keysValid, TAN_SPLINE, inTangent, computedTangent);

                // Compute the values from the keyframe along the
                // tangent 1/3 of the way to the previous and next
                // keyframes
                float tangent = computedTangent[1] / (computedTangent[0] * FPS);
                float prev13 = keyValues[1] - tangent * ((keyTimes[1] - keyTimes[0]) / 3.0f);
                float next13 = keyValues[1] + tangent * ((keyTimes[2] - keyTimes[1]) / 3.0f);

                if (isBetween(prev13, keyValues[0], keyValues[2]) &&
                        isBetween(next13, keyValues[0], keyValues[2])) {
                } else {
                    // Use flat tangent
                    computeTangent(keyTimes, keyValues, keysValid, TAN_FLAT, inTangent, computedTangent);
                }
            }

            return;
        }

        // Perform normalization
        // NOTE the scaling of the X coordinate -- this is needed to match Maya's math
        output[0] /= FPS;
        float len = (float) Math.sqrt(
                output[0] * output[0] +
                        output[1] * output[1]);
        if (len != 0.0f) {
            output[0] /= len;
            output[1] /= len;
        }
        // println("TAN LINEAR {output[0]} {output[1]}");
    }

    //=========================================================================
    // Loader.isBetween
    //=========================================================================
    boolean isBetween(
            float value,
            float v1,
            float v2) {
        return ((v1 <= value && value <= v2) ||
                (v1 >= value && value >= v2));
    }


    static class VertexHash {
        private int vertexIndex;
        private int normalIndex;
        private int[] uvIndices;

        VertexHash(
                int vertexIndex,
                int normalIndex,
                int[] uvIndices) {
            this.vertexIndex = vertexIndex;
            this.normalIndex = normalIndex;
            if (uvIndices != null) {
                this.uvIndices = (int[]) uvIndices.clone();
            }
        }

        @Override
        public int hashCode() {
            int code = vertexIndex;
            code *= 17;
            code += normalIndex;
            if (uvIndices != null) {
                for (int i = 0; i < uvIndices.length; i++) {
                    code *= 17;
                    code += uvIndices[i];
                }
            }
            return code;
        }

        @Override
        public boolean equals(Object arg) {
            if (arg == null || !(arg instanceof VertexHash)) {
                return false;
            }

            VertexHash other = (VertexHash) arg;
            if (vertexIndex != other.vertexIndex) {
                return false;
            }
            if (normalIndex != other.normalIndex) {
                return false;
            }
            if ((uvIndices != null) != (other.uvIndices != null)) {
                return false;
            }
            if (uvIndices != null) {
                if (uvIndices.length != other.uvIndices.length) {
                    return false;
                }
                for (int i = 0; i < uvIndices.length; i++) {
                    if (uvIndices[i] != other.uvIndices[i]) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private Mesh buildMeshData(List<MPolyFace.FaceData> faces, MFloat3Array normals) {

        TriangleMesh mesh = new TriangleMesh();

        // Setup vertexes
        float[] verts = mVerts.get();
        float[] tweaks = null;
        if (mPointTweaks != null) {
            tweaks = mPointTweaks.get();
        }
        float[] points = new float[verts.length * 3];
        for (int index = 0; index < verts.length; index += 3) {
            if (tweaks != null && tweaks.length > index + 2) {
                points[index] = verts[index] + tweaks[index];
                points[index + 1] = verts[index + 1] + tweaks[index + 1];
                points[index + 2] = verts[index + 2] + tweaks[index + 2];
            } else {
                points[index] = verts[index];
                points[index + 1] = verts[index + 1];
                points[index + 2] = verts[index + 2];
            }
        }
        mesh.getPoints().setAll(points);

        // copy UV as-is
        if (!addUVs(mesh, uvChannel)) {
            mesh.getTexCoords().setAll(0, 0);
        }

        int startFace = 0;
        int endFace = faces.size();

        int[] smGroups = SmoothGroups.calcSmoothGroups(faces, normals);

        List<Integer> ff = new ArrayList<Integer>();
        List<Integer> sg = new ArrayList<Integer>();

        for (int f = startFace; f < endFace; f++) {
            MPolyFace.FaceData faceData = faces.get(f);
            int[] faceEdges = faceData.getFaceEdges();
            int[][] uvData = faceData.getUVData();
            int[] uvIndices = uvData == null ? null : uvData[uvChannel];
            if (faceEdges != null && faceEdges.length > 0) {

                // Generate triangle fan about the first vertex
                int vIndex0 = edgeStart(faceEdges[0]);
                int uvIndex0 = uvIndices == null ? 0 : uvIndices[0];

                int vIndex1 = edgeStart(faceEdges[1]);
                int uvIndex1 = uvIndices == null ? 0 : uvIndices[1];

                for (int i = 2; i < faceEdges.length; i++) {
                    int vIndex2 = edgeStart(faceEdges[i]);
                    int uvIndex2 = uvIndices == null ? 0 : uvIndices[i];

                    ff.add(vIndex0);
                    ff.add(uvIndex0);
                    ff.add(vIndex1);
                    ff.add(uvIndex1);
                    ff.add(vIndex2);
                    ff.add(uvIndex2);
                    sg.add(smGroups[f]);

                    vIndex1 = vIndex2;
                    uvIndex1 = uvIndex2;
                }
            }
        }
        int[] fff = new int[ff.size()];
        for (int i = 0; i < fff.length; i++) {
            fff[i] = ff.get(i);
        }
        mesh.getFaces().setAll(fff);
        fff = null;
        ff = null;
        int[] sgsg = new int[sg.size()];
        for (int i = 0; i < sgsg.length; i++) {
            sgsg[i] = sg.get(i);
        }
        mesh.getFaceSmoothingGroups().setAll(sgsg);
        return mesh;
    }

    MNode resolveOutputMesh(MNode n) {
        MNode og;
        List<MPath> ogc0 = n.getPathsConnectingFrom("og[0]");
        if (ogc0.size() > 0) {
            og = ogc0.get(0).getTargetNode();
        } else {
            ogc0 = n.getPathsConnectingFrom("og");
            if (ogc0.size() > 0) {
                og = ogc0.get(0).getTargetNode();
            } else {
                return null;
            }
        }
        if (og.isInstanceOf(meshType)) {
            return og;
        }
        // println("r.OG={og}");
        while (og.isInstanceOf(groupPartsType)) {
            og = og.getPathsConnectingFrom("og").get(0).getTargetNode();
        }
        if (og.isInstanceOf(meshType)) {
            return og;
        }
        // println("r1.OG={og}");
        if (og == null) {
            return null;
        }
        return resolveOutputMesh(og);
    }

    MNode resolveInputMesh(MNode n) {
        return resolveInputMesh(n, true);
    }

    MNode resolveInputMesh(MNode n, boolean followBlend) {
        MNode groupParts;
        if (!n.isInstanceOf(groupPartsType)) {
            groupParts = n.getIncomingConnectionToType("ip[0].ig", "groupParts");
        } else {
            groupParts = n;
        }
        MNode origMesh = groupParts.getPathsConnectingTo("ig").get(0).getTargetNode();
        if (origMesh == null) {
            MNode tweak = groupParts.getIncomingConnectionToType("ig", "tweak");
            groupParts = tweak.getIncomingConnectionToType("ip[0].ig", "groupParts");
            origMesh =
                    groupParts.getPathsConnectingTo("ig").get(0).getTargetNode();
        }
        // println("N={n} ORIG_MESH={origMesh}");
        if (origMesh == null) {
            return null;
        }
        if (origMesh.isInstanceOf(meshType)) {
            return origMesh;
        }
        if (origMesh.isInstanceOf(blendShapeType)) {
            // return the blend shape's output
            return resolveOutputMesh(origMesh);
        }
        return resolveInputMesh(origMesh);
    }

    MNode resolveOrigInputMesh(MNode n) {

        MNode groupParts;
        if (!n.isInstanceOf(groupPartsType)) {
            groupParts = n.getIncomingConnectionToType("ip[0].ig", "groupParts");
        } else {
            groupParts = n;
        }
        MNode origMesh = groupParts.getPathsConnectingTo("ig").get(0).getTargetNode();
        if (origMesh == null) {
            MNode tweak = groupParts.getIncomingConnectionToType("ig", "tweak");
            groupParts = tweak.getIncomingConnectionToType("ip[0].ig", "groupParts");
            origMesh =
                    groupParts.getPathsConnectingTo("ig").get(0).getTargetNode();
        }
        if (origMesh == null) {
            return null;
        }
        // println("N={n} ORIG_MESH={origMesh}");
        if (origMesh.isInstanceOf(meshType)) {
            return origMesh;
        }
        return resolveOrigInputMesh(origMesh);
    }

    Affine convertMatrix(MFloatArray mayaMatrix) {
        if (mayaMatrix == null || mayaMatrix.getSize() < 16) {
            return new Affine();
        }

        Affine result = new Affine();
        result.setMxx(mayaMatrix.get(0 * 4 + 0));
        result.setMxy(mayaMatrix.get(1 * 4 + 0));
        result.setMxz(mayaMatrix.get(2 * 4 + 0));
        result.setMyx(mayaMatrix.get(0 * 4 + 1));
        result.setMyy(mayaMatrix.get(1 * 4 + 1));
        result.setMyz(mayaMatrix.get(2 * 4 + 1));
        result.setMzx(mayaMatrix.get(0 * 4 + 2));
        result.setMzy(mayaMatrix.get(1 * 4 + 2));
        result.setMzz(mayaMatrix.get(2 * 4 + 2));
        result.setTx(mayaMatrix.get(3 * 4 + 0));
        result.setTy(mayaMatrix.get(3 * 4 + 1));
        result.setTz(mayaMatrix.get(3 * 4 + 2));
        return result;
    }

}