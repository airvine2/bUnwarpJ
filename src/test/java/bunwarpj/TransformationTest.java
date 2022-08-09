package bunwarpj;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationTest {

    void setUpRegistration() {

    }

    @Test
    void doUnidirectionalRegistration_fine() throws Exception{

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        //test with resolution "fine"

        testContainer.options.max_scale_deformation = 2;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration();

        TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_fine");

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.saveMtxAsPng(warpedImageMtx, testContainer.outputFolder, "warped-source_fine");

    }

    @Test
    void doUnidirectionalRegistration_veryFine() throws Exception{

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        //test with resolution "very fine"

        testContainer.options.max_scale_deformation = 3;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration();

        TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs_veryFine");

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.saveMtxAsPng(warpedImageMtx, testContainer.outputFolder, "warped-source_veryFine");

    }

    @Test
    void doUnidirectionalRegistration_Optimization_2D_int() throws Exception{

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");


        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration_Setup(
                testContainer.options.min_scale_deformation,testContainer.options.max_scale_deformation);

        testContainer.warp.doUnidirectionalRegistration_Optimization(-1,-1);

        TestHelper.saveArrayCSV(TestHelper.doubleListToArray(testContainer.warp.getOptimizationErrorValues()),
                testContainer.outputFolder,"optimization-errors.csv", true);

        TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs");

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.saveMtxAsPng(warpedImageMtx, testContainer.outputFolder, "warped-source_veryFine");

    }

    @Test
    void doUnidirectionalRegistration_Optimization_1D_succeeded() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean saveResults = true;

        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData("C:\\images-as-csv\\test data_1D_optimization",
                "source.csv", "target.csv");

        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Float();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration_Setup(
                testContainer.options.min_scale_deformation,testContainer.options.max_scale_deformation);

        testContainer.warp.doUnidirectionalRegistration_Optimization(-1,-1);

        //apply transformation to source image
        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxFloat);

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

        //apply transformation to points
        double[] transformedPoint1 = MiscTools.approximateInverseCoords(new double[]{109d, 0}, ip,
                transformation_x,
                transformation_y);

        double[] transformedPoint2 = MiscTools.approximateInverseCoords(new double[]{130d, 0}, ip,
                transformation_x,
                transformation_y);

        if (saveResults) {
            TestHelper.saveArrayCSV(TestHelper.doubleListToArray(testContainer.warp.getOptimizationErrorValues()),
                    testContainer.outputFolder, "optimization-errors_float.csv", true);

            TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                    "transformationCoeffs_float");

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    testContainer.outputFolder, "warped-source_float.csv",
                    false);

            TestHelper.saveArrayCSV(transformation_x[0],
                    testContainer.outputFolder, "raw-transformation-X.csv",
                    false);
            TestHelper.saveArrayCSV(transformation_y[0],
                    testContainer.outputFolder, "raw-transformation-Y.csv",
                    false);

        } else {

        }

    }

    @Test
    void optimizeCoeffs_1D_int() throws Exception{

        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData("C:\\images-as-csv\\test data_1D_optimizeCoeffs",
                "source.csv", "target.csv");

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration_Setup(
                testContainer.options.min_scale_deformation,testContainer.options.max_scale_deformation);

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

        TestHelper.saveArrayCSV(cxTargetToSource,
                testContainer.outputFolder,"cxTargetToSource-initial.csv");
        TestHelper.saveArrayCSV(cyTargetToSource,
                testContainer.outputFolder,"cyTargetToSource-initial.csv");

        // Optimize deformation coefficients
        //the code below is equivalent to the private function call from within the Transformation class
        //optimizeCoeffs(intervals, stopThreshold, cxTargetToSource, cyTargetToSource, targetWidth > 1, targetHeight > 1);

        int intervals = (int)TestHelper.getPrivateField(testContainer.warp,"intervals");
        double stopThreshold = (double)TestHelper.getPrivateField(testContainer.warp,"stopThreshold");
        TestHelper.callPrivateMethod(testContainer.warp, "optimizeCoeffs",
                new Class[] {int.class, double.class, cxTargetToSource.getClass(), cyTargetToSource.getClass(),
                        boolean.class, boolean.class},
                intervals, stopThreshold, cxTargetToSource, cyTargetToSource, true, false);

        TestHelper.saveArrayCSV(cxTargetToSource,
                testContainer.outputFolder,"cxTargetToSource-optimized-once.csv");
        TestHelper.saveArrayCSV(cyTargetToSource,
                testContainer.outputFolder,"cyTargetToSource-optimized-once.csv");

        double[] optimErrorValues = testContainer.warp.getOptimizationErrorValues().stream().mapToDouble(d -> d).toArray();
        TestHelper.saveArrayCSV(optimErrorValues,
                testContainer.outputFolder,"optimization-error.csv", false);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.saveArrayCSV(warpedImageMtx[0],
                testContainer.outputFolder,"warped-source.csv", false);

    }

    @Test
    void optimizeCoeffs_1D_float() throws Exception{

        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData("C:\\images-as-csv\\test data_1D_optimizeCoeffs",
                "source.csv", "target.csv");

        testContainer.initializeTransformationInputs_Float();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration_Setup(testContainer.options.min_scale_deformation,
                testContainer.options.max_scale_deformation);

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

        TestHelper.saveArrayCSV(cxTargetToSource, testContainer.outputFolder,"cxTargetToSource-initial_float.csv");
        TestHelper.saveArrayCSV(cyTargetToSource, testContainer.outputFolder,"cyTargetToSource-initial_float.csv");

        // Optimize deformation coefficients
//        optimizeCoeffs(intervals, stopThreshold, cxTargetToSource, cyTargetToSource, targetWidth > 1, targetHeight > 1);

        int intervals = (int)TestHelper.getPrivateField(testContainer.warp,"intervals");
        double stopThreshold = (double)TestHelper.getPrivateField(testContainer.warp,"stopThreshold");
        TestHelper.callPrivateMethod(testContainer.warp, "optimizeCoeffs",
                new Class[] {int.class, double.class, cxTargetToSource.getClass(), cyTargetToSource.getClass(),
                        boolean.class, boolean.class},
                intervals, stopThreshold, cxTargetToSource, cyTargetToSource, true, false);

        TestHelper.saveArrayCSV(cxTargetToSource,
                testContainer.outputFolder,"cxTargetToSource-optimized-once_float.csv");
        TestHelper.saveArrayCSV(cyTargetToSource,
                testContainer.outputFolder,"cyTargetToSource-optimized-once_float.csv");

        double[] optimErrorValues = testContainer.warp.getOptimizationErrorValues().stream().mapToDouble(d -> d).toArray();
        TestHelper.saveArrayCSV(optimErrorValues,
                testContainer.outputFolder,"optimization-error_float.csv", false);

        //apply transformation to source image
        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxFloat);
        TestHelper.saveArrayCSV(warpedImageMtx[0],
                testContainer.outputFolder,"warped-source_float.csv", false);

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

//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data scaled_1D_optimization");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-2");
        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");

        TestContainer testContainer2 = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");

        int startResolution = 1;
        int endResolution = 4;

        testContainer.options.min_scale_deformation = 1;
        testContainer.options.max_scale_deformation = 4;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();
        testContainer.warp.doUnidirectionalRegistration_Setup(startResolution, endResolution);
        testContainer.warp.doUnidirectionalRegistration_Optimization(startResolution, endResolution);


        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();
        testContainer2.warp.doUnidirectionalRegistration_Setup(startResolution, endResolution);
        testContainer2.warp.doUnidirectionalRegistration_Optimization(startResolution, endResolution);


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

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");
        TestContainer testContainer2 = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");

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


        testContainer.warp.doUnidirectionalRegistration_Setup(0, 3);
        testContainer2.warp.doUnidirectionalRegistration_Setup(0, 3);

        testContainer.warp.doUnidirectionalRegistration_Optimization(0, 3);
        testContainer2.warp.doUnidirectionalRegistration_Optimization(0, 3);

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

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        TestContainer testContainer2 = new TestContainer("C:\\images-as-csv\\2D-int");

        int startResolution = 1;
        int endResolution = 4;

        testContainer.options.min_scale_deformation = 1;
        testContainer.options.max_scale_deformation = 4;
        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();
        testContainer.warp.doUnidirectionalRegistration_Setup(startResolution, endResolution);
        testContainer.warp.doUnidirectionalRegistration_Optimization(startResolution, endResolution);


        testContainer2.options.min_scale_deformation = 0;
        testContainer2.options.max_scale_deformation = 4;
        testContainer2.initializeTransformationInputs_Int();
        testContainer2.buildBSplineModels();
        testContainer2.initializeTransformationObject();
        testContainer2.warp.doUnidirectionalRegistration_Setup(startResolution, endResolution);
        testContainer2.warp.doUnidirectionalRegistration_Optimization(startResolution, endResolution);


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

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");
        TestContainer testContainer2 = new TestContainer("C:\\images-as-csv\\2D-int");

        int startingResolution = 0;
        int endResolution = 4;

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

        testContainer.warp.doUnidirectionalRegistration_Setup(startingResolution, endResolution);
        testContainer2.warp.doUnidirectionalRegistration_Setup(startingResolution, endResolution);

        //check if the transformations are equivalent after registration setup

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsX(),
                testContainer2.warp.getDirectDeformationCoefficientsX()));

        assertTrue(Arrays.deepEquals(
                testContainer.warp.getDirectDeformationCoefficientsY(),
                testContainer2.warp.getDirectDeformationCoefficientsY()));

        double[][] p11_1 = (double[][])TestHelper.getPrivateField(testContainer.warp, "P11_TargetToSource");
        double[][] p11_2= (double[][])TestHelper.getPrivateField(testContainer.warp, "P11_TargetToSource");
        assertTrue(Arrays.deepEquals(p11_1, p11_2));

        testContainer.warp.doUnidirectionalRegistration_Optimization(startingResolution, endResolution);
        testContainer2.warp.doUnidirectionalRegistration_Optimization(startingResolution, endResolution);

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
    void doUnidirectionalRegistration_Optimization_Autotune_1D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean saveResults = true;

//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data scaled_1D_optimization");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-2");
        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");



        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 3;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

//        TestContainer testContainer2 = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");
//        testContainer2.options.min_scale_deformation = 0;
//        testContainer2.options.max_scale_deformation = 4;
//
//        testContainer2.initializeTransformationInputs_Int();
//        testContainer2.buildBSplineModels();
//        testContainer2.initializeTransformationObject();


//        int minResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune();

        testContainer.warp.doUnidirectionalRegistration_Setup(0, 3);
        testContainer.warp.doUnidirectionalRegistration_Optimization(0, 3);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);

        //get the "raw" transformation
        ImagePlus ip = new ImagePlus("",new ByteProcessor(256,1));

        double[][] transformation_x =
                new double[ 1 ][ 256 ];
        double[][] transformation_y =
                new double[ 1 ][ 256 ];

        double[][] directTransfCoeffs_X = testContainer.warp.getDirectDeformationCoefficientsX();
        double[][] directTransfCoeffs_Y = testContainer.warp.getDirectDeformationCoefficientsY();

        MiscTools.convertElasticTransformationToRaw( ip, testContainer.warp.getIntervals(),
                directTransfCoeffs_X, directTransfCoeffs_Y,
                transformation_x, transformation_y );

        //apply transformation to points
        double[] transformedPoint1 = MiscTools.approximateInverseCoords(new double[]{109d, 0}, ip,
                transformation_x,
                transformation_y);

        double[] transformedPoint2 = MiscTools.approximateInverseCoords(new double[]{130d, 0}, ip,
                transformation_x,
                transformation_y);

        if (saveResults) {
            TestHelper.saveArrayCSV(TestHelper.doubleListToArray(testContainer.warp.getOptimizationErrorValues()),
                    testContainer.outputFolder, "optimization-errors.csv", false);

            TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                    "transformationCoeffs");

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    testContainer.outputFolder, "warped-source.csv", false);

            TestHelper.saveArrayCSV(transformation_x[0],
                    testContainer.outputFolder, "raw-transformation-X.csv", false);
            TestHelper.saveArrayCSV(transformation_y[0],
                    testContainer.outputFolder, "raw-transformation-Y.csv", false);

        } else {

        }

    }

    @Test
    void doUnidirectionalRegistration_Optimization_Autotune_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean saveResults = true;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

//        int minResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune_StartResolution();
//        int maxResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune_EndResolution();

        int[] resolutionsUsed = testContainer.warp.doUnidirectionalRegistration_AutoTune_Resolution(
                testContainer.sourceMtxInt, testContainer.targetMtxInt, true);

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

//        //get the "raw" transformation
//        ImagePlus ip = new ImagePlus("",new ByteProcessor(256,1));
//
//        double[][] transformation_x =
//                new double[ 1 ][ 256 ];
//        double[][] transformation_y =
//                new double[ 1 ][ 256 ];
//
//        double[][] directTransfCoeffs_X = testContainer.warp.getDirectDeformationCoefficientsX();
//        double[][] directTransfCoeffs_Y = testContainer.warp.getDirectDeformationCoefficientsY();
//
//        MiscTools.convertElasticTransformationToRaw( ip, testContainer.warp.getIntervals(),
//                directTransfCoeffs_X, directTransfCoeffs_Y,
//                transformation_x, transformation_y );

        if (saveResults) {
            TestHelper.saveArrayCSV(TestHelper.doubleListToArray(testContainer.warp.getOptimizationErrorValues()),
                    testContainer.outputFolder, "optimization-errors.csv", false);

            TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                    "transformationCoeffs");

            TestHelper.saveMtxAsPng(warpedImageMtx, testContainer.outputFolder, "warped-source_autotune_pixDiff");

//            TestHelper.saveArrayCSV(transformation_x[0],
//                    testContainer.outputFolder, "raw-transformation-X.csv", false);
//            TestHelper.saveArrayCSV(transformation_y[0],
//                    testContainer.outputFolder, "raw-transformation-Y.csv", false);

        } else {

        }

    }

}