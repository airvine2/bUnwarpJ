package bunwarpj;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationTest {

    /**
     * set to true if we are saving results to file, false if we are checking results against a file
     */
    private final boolean OVERWRITE_RESULTS_FILES = false;
    private Path resourcePath;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.resourcePath = Paths.get("", TestHelper.RESOURCES_DIR);
    }

    /**
     * //doUnidirectionalRegistration with resolution "fine" (min-max deformation is 0-2)
     * on a 2d int matrix
     * @throws Exception
     */
    @Test
    void doUnidirectionalRegistration_fine_2d_int() throws Exception{

        Path inputFolder = this.resourcePath.resolve("2D-int");
        TestContainer testContainer = new TestContainer(inputFolder.toString());

        testContainer.options.max_scale_deformation = 2;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration();

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        TestHelper.compareOrSaveTransform(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_fine", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(warpedImageMtx, testContainer.outputFolder,
                "warped-source_fine", OVERWRITE_RESULTS_FILES);

    }

    /**
     * //doUnidirectionalRegistration with resolution "very fine" (min-max deformation is 0-3)
     * @throws Exception
     */
    @Test
    void doUnidirectionalRegistration_veryFine_2d_int() throws Exception{

        Path inputFolder = this.resourcePath.resolve("2D-int");
        TestContainer testContainer = new TestContainer(inputFolder.toString());

        testContainer.options.max_scale_deformation = 3;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration();

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        TestHelper.compareOrSaveTransform(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_veryFine", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(warpedImageMtx, testContainer.outputFolder,
                "warped-source_veryFine", OVERWRITE_RESULTS_FILES);

    }

    /**
     * //doUnidirectionalRegistration with resolution "super fine" (min-max deformation is 0-4)
     * @throws Exception
     */
    @Test
    void doUnidirectionalRegistration_superFine_2D_int() throws Exception{

        Path inputFolder = this.resourcePath.resolve("2D-int");
        TestContainer testContainer = new TestContainer(inputFolder.toString());

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration(0,4);

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        TestHelper.compareOrSaveTransform(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_superFine", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(warpedImageMtx, testContainer.outputFolder,
                "warped-source_superFine", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveArray(TestHelper.doubleListToArray(testContainer.warp.getOptimizationErrorValues()),
                testContainer.outputFolder, "optimization-errors_superfine", OVERWRITE_RESULTS_FILES);

    }

    /**
     * doUnidirectionalRegistration on 1D data, expect it to successfully transform. operate on float values
     * and apply to points
     * @throws Exception
     */
    @Test
    void doUnidirectionalRegistration_1D() throws Exception{

        Path inputFolder = this.resourcePath.resolve("test data_1D_optimization");
        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData(inputFolder.toString(), "source.csv", "target.csv");

        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Float();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration(
                testContainer.options.min_scale_deformation,testContainer.options.max_scale_deformation);

        //apply transformation to source image
        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxFloat);

        TestHelper.compareOrSaveTransform(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_superFine", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(warpedImageMtx, testContainer.outputFolder,
                "warped-source_superFine_double", OVERWRITE_RESULTS_FILES);

        //get the "raw" transformation
        ImagePlus ip = new ImagePlus("",new ByteProcessor(256,1));

        double[][] transformation_x =
                new double[ 1 ][ 256 ];
        double[][] transformation_y =
                new double[ 1 ][ 256 ];

        MiscTools.convertElasticTransformationToRaw( ip, testContainer.warp.getIntervals(),
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer.warp.getDirectDeformationCoefficientsY(),
                transformation_x, transformation_y );

        TestHelper.compareOrSaveMtx(transformation_x, testContainer.outputFolder,
                "raw-transformation-X", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(transformation_y, testContainer.outputFolder,
                "raw-transformation-Y", OVERWRITE_RESULTS_FILES);

        //apply transformation to points
        double[] transformedPoint1 = MiscTools.approximateInverseCoords(new double[]{109d, 0}, ip,
                transformation_x,
                transformation_y);

        double[] transformedPoint2 = MiscTools.approximateInverseCoords(new double[]{130d, 0}, ip,
                transformation_x,
                transformation_y);

        assertTrue(Arrays.equals(transformedPoint1, new double[]{0,1}));
        assertTrue(Arrays.equals(transformedPoint1, new double[]{0,1}));

    }

    /**
     * Test one call to optimizeCoeffs, using 1d int data as input (source and target)
     * @throws Exception
     */
    @Test
    void optimizeCoeffs_1D_int() throws Exception{

        Path inputFolder = this.resourcePath.resolve("test data_1D_optimizeCoeffs");
        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData(inputFolder.toString(), "source.csv", "target.csv");

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        BSplineModel source = (BSplineModel)(TestHelper.getPrivateField(testContainer.warp, "source"));
        BSplineModel target = (BSplineModel)(TestHelper.getPrivateField(testContainer.warp, "target"));

        source.popFromPyramid();
        target.popFromPyramid();

        TestHelper.setPrivateField(testContainer.warp, "targetCurrentHeight", target.getCurrentHeight());
        TestHelper.setPrivateField(testContainer.warp, "targetCurrentWidth", target.getCurrentWidth());
        TestHelper.setPrivateField(testContainer.warp, "targetFactorHeight", target.getFactorHeight());
        TestHelper.setPrivateField(testContainer.warp, "targetFactorWidth", target.getFactorWidth());
        TestHelper.setPrivateField(testContainer.warp, "sourceCurrentHeight", source.getCurrentHeight());
        TestHelper.setPrivateField(testContainer.warp, "sourceCurrentWidth", source.getCurrentWidth());
        TestHelper.setPrivateField(testContainer.warp, "sourceFactorHeight", source.getFactorHeight());
        TestHelper.setPrivateField(testContainer.warp, "sourceFactorWidth", source.getFactorWidth());

        int intervals = (int)Math.pow(2, testContainer.options.min_scale_deformation);
        TestHelper.setPrivateField(testContainer.warp, "intervals", intervals);

        TestHelper.setPrivateField(testContainer.warp, "cxTargetToSource", new double[intervals+3][intervals+3]);
        TestHelper.setPrivateField(testContainer.warp, "cyTargetToSource", new double[intervals+3][intervals+3]);

        TestHelper.callPrivateMethod(testContainer.warp, "buildRegularizationTemporary",
                new Class[] {int.class, boolean.class},
                intervals, false);

        TestHelper.callPrivateMethod(testContainer.warp, "setupAffineMtx_TargetToSource",
                new Class[] {});

//        optimizationErrorValues = new ArrayList<>();
        TestHelper.setPrivateField(testContainer.warp, "optimizationErrorValues", new ArrayList<>());

        // residues for landmarks
        double [] dxTargetToSource = new double[0];
        double [] dyTargetToSource = new double[0];
//        computeInitialResidues(dxTargetToSource,dyTargetToSource, false);
        TestHelper.callPrivateMethod(testContainer.warp, "computeInitialResidues",
                new Class[] {dxTargetToSource.getClass(), dyTargetToSource.getClass(), boolean.class},
                dxTargetToSource,dyTargetToSource, false);

//        int s = min_scale_deformation;
        int s = (int)TestHelper.getPrivateField(testContainer.warp, "min_scale_deformation");
        int step = 0;

//        int currentDepth = target.getCurrentDepth();
        int currentDepth = ((BSplineModel)TestHelper.getPrivateField(testContainer.warp,"target")).getCurrentDepth();

        // Update the deformation coefficients with the error of the landmarks
        //the result of this function is to calculate values for cxTargetToSource and cyTargetToSource
//        calculateNewCoefficients(dxTargetToSource, dyTargetToSource, s, step);
        TestHelper.callPrivateMethod(testContainer.warp, "calculateNewCoefficients",
                new Class[] {dxTargetToSource.getClass(), dyTargetToSource.getClass(), int.class, int.class},
                dxTargetToSource,dyTargetToSource, s, step);

        //check cxTargetToSource and cyTargetToSource
        double[][] cxTargetToSource = (double[][])TestHelper.getPrivateField(testContainer.warp, "cxTargetToSource");
        double[][] cyTargetToSource = (double[][])TestHelper.getPrivateField(testContainer.warp, "cyTargetToSource");

        TestHelper.compareOrSaveMtx(cxTargetToSource,
                testContainer.outputFolder,"cxTargetToSource-initial", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(cyTargetToSource,
                testContainer.outputFolder,"cyTargetToSource-initial", OVERWRITE_RESULTS_FILES);

        // Optimize deformation coefficients
        //the code below is equivalent to the private function call from within the Transformation class
        //optimizeCoeffs(intervals, stopThreshold, cxTargetToSource, cyTargetToSource, targetWidth > 1, targetHeight > 1);

        intervals = (int)TestHelper.getPrivateField(testContainer.warp,"intervals");
        double stopThreshold = (double)TestHelper.getPrivateField(testContainer.warp,"stopThreshold");
        TestHelper.callPrivateMethod(testContainer.warp, "optimizeCoeffs",
                new Class[] {int.class, double.class, cxTargetToSource.getClass(), cyTargetToSource.getClass(),
                        boolean.class, boolean.class},
                intervals, stopThreshold, cxTargetToSource, cyTargetToSource, true, false);

        TestHelper.compareOrSaveMtx(cxTargetToSource,
                testContainer.outputFolder,"cxTargetToSource-optimized-once", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(cyTargetToSource,
                testContainer.outputFolder,"cyTargetToSource-optimized-once", OVERWRITE_RESULTS_FILES);

        double[] optimErrorValues = testContainer.warp.getOptimizationErrorValues().stream().mapToDouble(d -> d).toArray();

        TestHelper.compareOrSaveArray(optimErrorValues,
                testContainer.outputFolder,"optimization-error", OVERWRITE_RESULTS_FILES);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.compareOrSaveMtx(warpedImageMtx,
                testContainer.outputFolder,"warped-source", OVERWRITE_RESULTS_FILES);

    }

    /**
     * Test one call to optimizeCoeffs, using 1d float data as input (source and target)
     * @throws Exception
     */
    @Test
    void optimizeCoeffs_1D_float() throws Exception{

        Path inputFolder = this.resourcePath.resolve("test data_1D_optimizeCoeffs");
        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData(inputFolder.toString(), "source.csv", "target.csv");

        testContainer.initializeTransformationInputs_Float();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        BSplineModel source = (BSplineModel)(TestHelper.getPrivateField(testContainer.warp, "source"));
        BSplineModel target = (BSplineModel)(TestHelper.getPrivateField(testContainer.warp, "target"));

        source.popFromPyramid();
        target.popFromPyramid();

        TestHelper.setPrivateField(testContainer.warp, "targetCurrentHeight", target.getCurrentHeight());
        TestHelper.setPrivateField(testContainer.warp, "targetCurrentWidth", target.getCurrentWidth());
        TestHelper.setPrivateField(testContainer.warp, "targetFactorHeight", target.getFactorHeight());
        TestHelper.setPrivateField(testContainer.warp, "targetFactorWidth", target.getFactorWidth());
        TestHelper.setPrivateField(testContainer.warp, "sourceCurrentHeight", source.getCurrentHeight());
        TestHelper.setPrivateField(testContainer.warp, "sourceCurrentWidth", source.getCurrentWidth());
        TestHelper.setPrivateField(testContainer.warp, "sourceFactorHeight", source.getFactorHeight());
        TestHelper.setPrivateField(testContainer.warp, "sourceFactorWidth", source.getFactorWidth());

        int intervals = (int)Math.pow(2, testContainer.options.min_scale_deformation);
        TestHelper.setPrivateField(testContainer.warp, "intervals", intervals);

        TestHelper.setPrivateField(testContainer.warp, "cxTargetToSource", new double[intervals+3][intervals+3]);
        TestHelper.setPrivateField(testContainer.warp, "cyTargetToSource", new double[intervals+3][intervals+3]);

        TestHelper.callPrivateMethod(testContainer.warp, "buildRegularizationTemporary",
                new Class[] {int.class, boolean.class},
                intervals, false);

        TestHelper.callPrivateMethod(testContainer.warp, "setupAffineMtx_TargetToSource",
                new Class[] {});

//        optimizationErrorValues = new ArrayList<>();
        TestHelper.setPrivateField(testContainer.warp, "optimizationErrorValues", new ArrayList<>());

        // residues for landmarks
        double [] dxTargetToSource = new double[0];
        double [] dyTargetToSource = new double[0];
//        computeInitialResidues(dxTargetToSource,dyTargetToSource, false);
        TestHelper.callPrivateMethod(testContainer.warp, "computeInitialResidues",
                new Class[] {dxTargetToSource.getClass(), dyTargetToSource.getClass(), boolean.class},
                dxTargetToSource,dyTargetToSource, false);

        int s = (int)TestHelper.getPrivateField(testContainer.warp, "min_scale_deformation");
        int step = 0;

//        int currentDepth = target.getCurrentDepth();
        int currentDepth = ((BSplineModel)TestHelper.getPrivateField(testContainer.warp,"target")).getCurrentDepth();

        // Update the deformation coefficients with the error of the landmarks
        //the result of this function is to calculate values for cxTargetToSource and cyTargetToSource
//        calculateNewCoefficients(dxTargetToSource, dyTargetToSource, s, step);
        TestHelper.callPrivateMethod(testContainer.warp, "calculateNewCoefficients",
                new Class[] {dxTargetToSource.getClass(), dyTargetToSource.getClass(), int.class, int.class},
                dxTargetToSource,dyTargetToSource, s, step);

        //check cxTargetToSource and cyTargetToSource
        double[][] cxTargetToSource = (double[][])TestHelper.getPrivateField(testContainer.warp, "cxTargetToSource");
        double[][] cyTargetToSource = (double[][])TestHelper.getPrivateField(testContainer.warp, "cyTargetToSource");

        TestHelper.compareOrSaveMtx(cxTargetToSource, testContainer.outputFolder,
                "cxTargetToSource-initial_float",
                OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(cyTargetToSource, testContainer.outputFolder,
                "cyTargetToSource-initial_float",
                OVERWRITE_RESULTS_FILES);

        // Optimize deformation coefficients
//        optimizeCoeffs(intervals, stopThreshold, cxTargetToSource, cyTargetToSource, targetWidth > 1, targetHeight > 1);

        intervals = (int)TestHelper.getPrivateField(testContainer.warp,"intervals");
        double stopThreshold = (double)TestHelper.getPrivateField(testContainer.warp,"stopThreshold");
        TestHelper.callPrivateMethod(testContainer.warp, "optimizeCoeffs",
                new Class[] {int.class, double.class, cxTargetToSource.getClass(), cyTargetToSource.getClass(),
                        boolean.class, boolean.class},
                intervals, stopThreshold, cxTargetToSource, cyTargetToSource, true, false);

        TestHelper.compareOrSaveMtx(cxTargetToSource,
                testContainer.outputFolder,"cxTargetToSource-optimized-once_float", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(cyTargetToSource,
                testContainer.outputFolder,"cyTargetToSource-optimized-once_float", OVERWRITE_RESULTS_FILES);

        double[] optimErrorValues = testContainer.warp.getOptimizationErrorValues().stream().mapToDouble(d -> d).toArray();
        TestHelper.compareOrSaveArray(optimErrorValues,
                testContainer.outputFolder,"optimization-error_float", OVERWRITE_RESULTS_FILES);

        //apply transformation to source image
        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxFloat);
        TestHelper.compareOrSaveMtx(warpedImageMtx,
                testContainer.outputFolder,"warped-source_float", OVERWRITE_RESULTS_FILES);

    }

    @Test
    @DisplayName("doUnidirectionalRegistration_Optimization_startingDeformationDetail_1D")
    /**
     * (1D data input) Test that doUnidirectionalRegistration_Optimization gives the same results
     * if you initialize the transformation with min_scale_deformation = 1,
     * or if you initialize the transformation with min_scale_deformation = 0
     * and then pass the value 1 as input parameter startingDeformationDetail to doUnidirectionalRegistration_Optimization
     * (that you can control the starting deformation detail regardless of how the transform was initialized)
     */
    void doUnidirectionalRegistration_Optimization_startingDeformationDetail_1D() throws Exception{

        Path inputFolder = this.resourcePath.resolve("test data_1D_debug-3");
        TestContainer testContainer = new TestContainer(inputFolder.toString());
        TestContainer testContainer2 = new TestContainer(inputFolder.toString());

        int startResolution = 1;
        int endResolution = 4;

        testContainer.options.min_scale_deformation = 1;
        testContainer.options.max_scale_deformation = 4;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();
        testContainer.warp.doUnidirectionalRegistration(startResolution, endResolution);


        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();
        testContainer2.warp.doUnidirectionalRegistration(startResolution, endResolution);


        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        //apply transformation to source image
        int[][] warpedImageMtx2 = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer2.warp,
                testContainer.sourceMtxInt);

        //compare transformation coefficients
        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        assertTrue(Arrays.deepEquals(warpedImageMtx, warpedImageMtx2));

    }

    @Test
    /**
     * (1D data input) Test that doUnidirectionalRegistration_Optimization gives the same results
     * if you initialize the transformation with max_scale_deformation = 3,
     * or if you initialize the transformation with max_scale_deformation = 4
     * and then pass the value 3 as input parameter startingDeformationDetail to
     * doUnidirectionalRegistration_Optimization
     * (that you can control the max deformation detail regardless of how the transform was initialized)
     */
    void doUnidirectionalRegistration_Optimization_endingDeformationDetail_1D() throws Exception{
        Path inputFolder = this.resourcePath.resolve("test data_1D_debug-3");
        TestContainer testContainer = new TestContainer(inputFolder.toString());
        TestContainer testContainer2 = new TestContainer(inputFolder.toString());

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 3;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration(0, 3);
        testContainer2.warp.doUnidirectionalRegistration(0, 3);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        //apply transformation to source image
        int[][] warpedImageMtx2 = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer2.warp,
                testContainer.sourceMtxInt);

        //compare transformation coefficients
        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        assertTrue(Arrays.deepEquals(warpedImageMtx, warpedImageMtx2));

    }

    @Test
    /**
     * (2D data input) Test that doUnidirectionalRegistration_Optimization gives the same results
     * if you initialize the transformation with min_scale_deformation = 1,
     * or if you initialize the transformation with min_scale_deformation = 0
     * and then pass the value 1 as input parameter startingDeformationDetail to doUnidirectionalRegistration_Optimization
     * (that you can control the starting deformation detail regardless of how the transform was initialized)
     */
    void doUnidirectionalRegistration_Optimization_startingDeformationDetail_2D() throws Exception{

        Path inputFolder = this.resourcePath.resolve("2D-int");
        TestContainer testContainer = new TestContainer(inputFolder.toString());
        TestContainer testContainer2 = new TestContainer(inputFolder.toString());

        int startResolution = 1;
        int endResolution = 4;

        testContainer.options.min_scale_deformation = startResolution;
        testContainer.options.max_scale_deformation = endResolution;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();
        testContainer.warp.doUnidirectionalRegistration(startResolution, endResolution);


        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();
        testContainer2.warp.doUnidirectionalRegistration(startResolution, endResolution);


        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        //apply transformation to source image
        int[][] warpedImageMtx2 = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer2.warp,
                testContainer.sourceMtxInt);

        //compare transformation coefficients
        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        assertTrue(Arrays.deepEquals(warpedImageMtx, warpedImageMtx2));

    }

    @Test
    /**
     * (2D data input) Test that doUnidirectionalRegistration_Optimization gives the same results
     * if you initialize the transformation with max_scale_deformation = 3,
     * or if you initialize the transformation with max_scale_deformation = 4
     * and then pass the value 3 as input parameter startingDeformationDetail to
     * doUnidirectionalRegistration_Optimization
     * (that you can control the max deformation detail regardless of how the transform was initialized)
     */
    void doUnidirectionalRegistration_Optimization_endingDeformationDetail_2D() throws Exception{

        Path inputFolder = this.resourcePath.resolve("2D-int");
        TestContainer testContainer = new TestContainer(inputFolder.toString());
        TestContainer testContainer2 = new TestContainer(inputFolder.toString());

        int startingResolution = 0;
        int endResolution = 3;

        //initialize a Transformation with the resolutions we are testing
        testContainer.options.min_scale_deformation = startingResolution;
        testContainer.options.max_scale_deformation = endResolution;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        //initialize a Transformation with the full range of resolutions
        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();

        //do registration, specifying the resolutions to use
        testContainer.warp.doUnidirectionalRegistration(startingResolution, endResolution);
        testContainer2.warp.doUnidirectionalRegistration(startingResolution, endResolution);

        //apply each transformation to the source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        int[][] warpedImageMtx2 = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer2.warp,
                testContainer.sourceMtxInt);

        //compare transformation coefficients
        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        //compare warped source images
        assertTrue(Arrays.deepEquals(warpedImageMtx, warpedImageMtx2));

    }

    @Test
    /**
     * (1D data input) Test doUnidirectionalRegistration_AutoTune_StartResolution,
     * which chooses the optimal starting resolution, leaving the ending resolution fixed
     */
    void doUnidirectionalRegistration_AutoTune_StartResolution_1D() throws Exception{

        Path inputFolder = this.resourcePath.resolve("test data_1D_debug-3");
        TestContainer testContainer = new TestContainer(inputFolder.toString());

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int minResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                Transformation.AutoresolutionDirection.start)[0];

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        int expectedMinResolution = 0;
        assertTrue(expectedMinResolution == minResolution_used);

        TestHelper.compareOrSaveTransform(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_autotune_startRes", OVERWRITE_RESULTS_FILES);
        TestHelper.compareOrSaveMtx(warpedImageMtx, testContainer.outputFolder,
                "warped-source_autotune_startRes", OVERWRITE_RESULTS_FILES);

    }

    @Test
    /**
     * (1D data input) Test doUnidirectionalRegistration_AutoTune_EndResolution,
     * which chooses the optimal ending resolution, leaving the starting resolution fixed
     */
    void doUnidirectionalRegistration_AutoTune_EndResolution_1D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int maxResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution("end")[1];

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_endRes");

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    testContainer.outputFolder, "warped-source_autotune_endRes.csv", false);

        } else {

            int expectedMaxResolution = 4;
            assertTrue(expectedMaxResolution == maxResolution_used);

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_endRes");

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, "warped-source_autotune_endRes.csv").toString()
            );
            assertTrue(Arrays.equals(warpedImageMtx[0], expectedWarpedImageMtx[0]));

        }

    }

    @Test
    /**
     * (1D data input) Test doUnidirectionalRegistration_AutoTune_Resolution,
     * which chooses the optimal starting and ending resolutions
     * error is measured by L2 pixel diff between the warped source image and the target image
     * instead of using the bUnwarpJ error term
     */
    void doUnidirectionalRegistration_Autotune_Resolution_1D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int[] resolutionsUsed = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                testContainer.sourceMtxInt, testContainer.targetMtxInt, Transformation.AutoresolutionDirection.both);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_Res");

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    testContainer.outputFolder, "warped-source_autotune_Res.csv", false);

        } else {

            int[] expectedResolutions = new int[] {0,4};
            assertTrue(Arrays.equals(expectedResolutions, resolutionsUsed));

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_Res");

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, "warped-source_autotune_Res.csv").toString()
            );
            assertTrue(Arrays.equals(warpedImageMtx[0], expectedWarpedImageMtx[0]));

        }

    }

    @Test
    /**
     * (1D data input) Test doUnidirectionalRegistration_AutoTune_Resolution,
     * which chooses the optimal starting and ending resolutions
     * error is measured by L2 pixel diff between the warped source image and the target image
     * Autotune needs improvement because this was giving a transform that made histogram all 0s
     */
    void doUnidirectionalRegistration_Autotune_Resolution_1D_nonoptimal() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        String inputFolder = this.resourcePath.resolve(
                Paths.get("csv/1D-bad-transform-autotune")).toString();

        TestContainer testContainer = new TestContainer(inputFolder);

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();
        testContainer.warp.setImageSumDecreaseThreshold(0.5);

        int[] resolutionsUsed = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                testContainer.sourceMtxInt, testContainer.targetMtxInt,
                Transformation.AutoresolutionDirection.both);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        assertTrue(Arrays.stream(warpedImageMtx[0]).max().getAsInt() > 0);

        String baseFileName = "doUnidirectionalRegistration_Autotune_Resolution_1D_nonoptimal";
        String coeffsFile = baseFileName + "_coeffs";
        String warpedFile = baseFileName + "warped.csv";

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, coeffsFile);

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    testContainer.outputFolder, warpedFile, false);

        } else {

            int[] expectedResolutions = new int[] {2,3};
            assertTrue(Arrays.equals(expectedResolutions, resolutionsUsed));

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, coeffsFile);

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, warpedFile).toString()
            );
            assertTrue(Arrays.equals(warpedImageMtx[0], expectedWarpedImageMtx[0]));

        }

    }

    @Test
    /**
     * (2D data input) Test doUnidirectionalRegistration_AutoTune_StartResolution,
     * which chooses the optimal starting resolution, leaving the ending resolution fixed
     */
    void doUnidirectionalRegistration_AutoTune_StartResolution_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int minResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                Transformation.AutoresolutionDirection.start)[0];

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_startRes");

            TestHelper.saveMtxAsPng(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_startRes");

            TestHelper.saveArrayCSV(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_startRes.csv");

        } else {

            int expectedMinResolution = 2;
            assertTrue(expectedMinResolution == minResolution_used);

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_startRes");

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, "warped-source_autotune_startRes.csv").toString()
            );
            assertTrue(Arrays.deepEquals(warpedImageMtx, expectedWarpedImageMtx));

        }
    }

    @Test
    /**
     * (2D data input) Test doUnidirectionalRegistration_AutoTune_EndResolution,
     * which chooses the optimal ending resolution, leaving the starting resolution fixed
     */
    void doUnidirectionalRegistration_AutoTune_EndResolution_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int maxResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                Transformation.AutoresolutionDirection.end)[1];

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_endRes");

            TestHelper.saveMtxAsPng(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_endRes");

            TestHelper.saveArrayCSV(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_endRes.csv");

        } else {

            int expectedMaxResolution = 4;
            assertTrue(expectedMaxResolution == maxResolution_used);

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_endRes");

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, "warped-source_autotune_endRes.csv").toString()
            );
            assertTrue(Arrays.deepEquals(warpedImageMtx, expectedWarpedImageMtx));

        }

    }

    @Test
    /**
     * (2D data input) Test doUnidirectionalRegistration_AutoTune_Resolution,
     * which chooses the optimal starting and ending resolutions
     * error is measured by L2 pixel diff between the warped source image and the target image
     * instead of using the bUnwarpJ error term
     */
    void doUnidirectionalRegistration_Autotune_Resolution_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int[] resolutionsUsed = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                testContainer.sourceMtxInt, testContainer.targetMtxInt, Transformation.AutoresolutionDirection.both);

        //TIMING TEST

//        double startTime = System.currentTimeMillis();
//        int[] resolutionsUsed = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
//                testContainer.sourceMtxInt, testContainer.targetMtxInt, true);
//        double endTime = System.currentTimeMillis();
//        double executionTimeMS = endTime - startTime;
//
//        double startTime2 = System.currentTimeMillis();
//        //apply transformation to source image
//        resolutionsUsed = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
//                testContainer.sourceMtxInt, testContainer.targetMtxInt, false);
//        double endTime2 = System.currentTimeMillis();
//
//        double executionTimeMS2 = endTime2 - startTime2;

        //END TIMING TEST

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_Res");

            TestHelper.saveMtxAsPng(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_Res");

            TestHelper.saveArrayCSV(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_Res.csv");

        } else {

            int[] expectedResolutions = new int[] {1,4};
            assertTrue(Arrays.equals(expectedResolutions, resolutionsUsed));

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_Res");

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, "warped-source_autotune_Res.csv").toString()
            );
            assertTrue(Arrays.deepEquals(warpedImageMtx, expectedWarpedImageMtx));

        }

    }

    @Test
    void reset() throws Exception {
        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");
        TestContainer testContainer2 = new TestContainer("C:\\images-as-csv\\2D-int");

        int startingResolution = 0;
        int endResolution = 3;

        //initialize a Transformation with the resolutions we are testing
        testContainer.options.min_scale_deformation = startingResolution;
        testContainer.options.max_scale_deformation = endResolution;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        //initialize a Transformation with the full range of resolutions
        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();


        testContainer2.warp.doUnidirectionalRegistration(0, 4);
        double[][] coeffsX = testContainer2.warp.getDirectDeformationCoefficientsX();
        double[][] coeffsY = testContainer2.warp.getDirectDeformationCoefficientsY();
        testContainer2.warp.reset();

        //compare the source and target between testContainer and testContainer2, they should be equivalent
        BSplineModel source1 = (BSplineModel)TestHelper.getPrivateField(testContainer.warp, "source");
        BSplineModel source2 = (BSplineModel)TestHelper.getPrivateField(testContainer2.warp, "source");

        assertTrue(Arrays.equals(source1.getCoefficients(), source2.getCoefficients()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        testContainer.warp.doUnidirectionalRegistration(startingResolution, endResolution);
        testContainer2.warp.doUnidirectionalRegistration(startingResolution, endResolution);

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        //apply each transformation to the source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        int[][] warpedImageMtx2 = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer2.warp,
                testContainer.sourceMtxInt);

        //compare warped source images
        assertTrue(Arrays.deepEquals(warpedImageMtx, warpedImageMtx2));

        //reset coeffs to the first optimization
        TestHelper.setPrivateField(testContainer2.warp, "cxTargetToSource", coeffsX);
        TestHelper.setPrivateField(testContainer2.warp, "cyTargetToSource", coeffsY);

        TestHelper.saveMtxAsPng(warpedImageMtx2, testContainer.outputFolder, "warped-source_reset");

    }

    @Test
    /**
     * (2D data input) Test doUnidirectionalRegistration_AutoTune_Weights,
     * which chooses the optimal divergence and curl weights
     */
    void doUnidirectionalRegistration_Autotune_Weights_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = true;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        double bestDivWeight = testContainer.warp.doUnidirectionalRegistration_AutoTune_Weights(
                testContainer.sourceMtxInt, testContainer.targetMtxInt, true);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_Weight");

            TestHelper.saveMtxAsPng(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_Weight");

            TestHelper.saveArrayCSV(warpedImageMtx,
                    testContainer.outputFolder, "warped-source_autotune_Weight.csv");

        } else {

//            int[] expectedResolutions = new int[] {1,4};
//            assertTrue(Arrays.equals(expectedResolutions, resolutionsUsed));

            TestHelper.compareTransformationCoeffsToFile(testContainer.warp,
                    testContainer.outputFolder, "transformationCoeffs_autotune_Weight");

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, "warped-source_autotune_Res.Weight").toString()
            );
            assertTrue(Arrays.deepEquals(warpedImageMtx, expectedWarpedImageMtx));

        }

    }



}