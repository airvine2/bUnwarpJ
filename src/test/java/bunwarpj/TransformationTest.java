package bunwarpj;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;

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
        TestHelper.saveMtxAsPng(warpedImageMtx, "warped-source_fine", testContainer.outputFolder);

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
        TestHelper.saveMtxAsPng(warpedImageMtx, "warped-source_veryFine", testContainer.outputFolder);

    }

    @Test
    void doUnidirectionalRegistration_Optimization_2D_int() throws Exception{

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");


        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration_Setup();

        testContainer.warp.doUnidirectionalRegistration_Optimization(-1);

        TestHelper.saveArrayCSV(TestHelper.doubleListToArray(testContainer.warp.getOptimizationErrorValues()),
                Paths.get(testContainer.outputFolder,"optimization-errors.csv").toString(), true);

        TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                "transformationCoeffs");

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.saveMtxAsPng(warpedImageMtx, "warped-source_veryFine", testContainer.outputFolder);

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

        testContainer.warp.doUnidirectionalRegistration_Setup();

        testContainer.warp.doUnidirectionalRegistration_Optimization(-1);

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
                    Paths.get(testContainer.outputFolder, "optimization-errors_float.csv").toString(), true);

            TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                    "transformationCoeffs_float");

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    Paths.get(testContainer.outputFolder, "warped-source_float.csv").toString(),
                    false);

            TestHelper.saveArrayCSV(transformation_x[0],
                    Paths.get(testContainer.outputFolder, "raw-transformation-X.csv").toString(),
                    false);
            TestHelper.saveArrayCSV(transformation_y[0],
                    Paths.get(testContainer.outputFolder, "raw-transformation-Y.csv").toString(),
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

        testContainer.warp.doUnidirectionalRegistration_Setup();

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
                Paths.get(testContainer.outputFolder,"cxTargetToSource-initial.csv").toString());
        TestHelper.saveArrayCSV(cyTargetToSource,
                Paths.get(testContainer.outputFolder,"cyTargetToSource-initial.csv").toString());


        // Optimize deformation coefficients
//        optimizeCoeffs(intervals, stopThreshold, cxTargetToSource, cyTargetToSource, targetWidth > 1, targetHeight > 1);

        int intervals = (int)TestHelper.getPrivateField(testContainer.warp,"intervals");
        double stopThreshold = (double)TestHelper.getPrivateField(testContainer.warp,"stopThreshold");
        TestHelper.callPrivateMethod(testContainer.warp, "optimizeCoeffs",
                new Class[] {int.class, double.class, cxTargetToSource.getClass(), cyTargetToSource.getClass(),
                        boolean.class, boolean.class},
                intervals, stopThreshold, cxTargetToSource, cyTargetToSource, true, false);

        TestHelper.saveArrayCSV(cxTargetToSource,
                Paths.get(testContainer.outputFolder,"cxTargetToSource-optimized-once.csv").toString());
        TestHelper.saveArrayCSV(cyTargetToSource,
                Paths.get(testContainer.outputFolder,"cyTargetToSource-optimized-once.csv").toString());

        double[] optimErrorValues = testContainer.warp.getOptimizationErrorValues().stream().mapToDouble(d -> d).toArray();
        TestHelper.saveArrayCSV(optimErrorValues,
                Paths.get(testContainer.outputFolder,"optimization-error.csv").toString(),
                false);

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxInt);
        TestHelper.saveArrayCSV(warpedImageMtx[0],
                Paths.get(testContainer.outputFolder,"warped-source.csv").toString(),
                false);

    }

    @Test
    void optimizeCoeffs_1D_float() throws Exception{

        TestContainer testContainer = new TestContainer();
        testContainer.loadUnscaledFloatCsvData("C:\\images-as-csv\\test data_1D_optimizeCoeffs",
                "source.csv", "target.csv");

        testContainer.initializeTransformationInputs_Float();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        testContainer.warp.doUnidirectionalRegistration_Setup();

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
                Paths.get(testContainer.outputFolder,"cxTargetToSource-initial_float.csv").toString());
        TestHelper.saveArrayCSV(cyTargetToSource,
                Paths.get(testContainer.outputFolder,"cyTargetToSource-initial_float.csv").toString());


        // Optimize deformation coefficients
//        optimizeCoeffs(intervals, stopThreshold, cxTargetToSource, cyTargetToSource, targetWidth > 1, targetHeight > 1);

        int intervals = (int)TestHelper.getPrivateField(testContainer.warp,"intervals");
        double stopThreshold = (double)TestHelper.getPrivateField(testContainer.warp,"stopThreshold");
        TestHelper.callPrivateMethod(testContainer.warp, "optimizeCoeffs",
                new Class[] {int.class, double.class, cxTargetToSource.getClass(), cyTargetToSource.getClass(),
                        boolean.class, boolean.class},
                intervals, stopThreshold, cxTargetToSource, cyTargetToSource, true, false);

        TestHelper.saveArrayCSV(cxTargetToSource,
                Paths.get(testContainer.outputFolder,"cxTargetToSource-optimized-once_float.csv").toString());
        TestHelper.saveArrayCSV(cyTargetToSource,
                Paths.get(testContainer.outputFolder,"cyTargetToSource-optimized-once_float.csv").toString());

        double[] optimErrorValues = testContainer.warp.getOptimizationErrorValues().stream().mapToDouble(d -> d).toArray();
        TestHelper.saveArrayCSV(optimErrorValues,
                Paths.get(testContainer.outputFolder,"optimization-error_float.csv").toString(),
                false);

        //apply transformation to source image
        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(testContainer.warp,
                testContainer.sourceMtxFloat);
        TestHelper.saveArrayCSV(warpedImageMtx[0],
                Paths.get(testContainer.outputFolder,"warped-source_float.csv").toString(),
                false);

    }

    @Test
    void doUnidirectionalRegistration_Optimization_1D_fails() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean saveResults = true;

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data scaled_1D_optimization");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-2");
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\test data_1D_debug-3");



        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        testContainer.initializeTransformationInputs_Int();
        testContainer.buildBSplineModels();
        testContainer.initializeTransformationObject();

        int minResolution_used = testContainer.warp.doUnidirectionalRegistration_AutoTune();

//        testContainer.warp.doUnidirectionalRegistration_Setup();

//        int minDeformation = 0;
//        int maxDeformation = 4;
//        boolean suceeded = testContainer.warp.doUnidirectionalRegistration_AutoTune();
//        while (!suceeded) {
//            testContainer.options.min_scale_deformation = 0;
//            testContainer.options.max_scale_deformation = 4;
//            testContainer.initializeTransformationInputs_Int();
//            testContainer.buildBSplineModels();
//            testContainer.initializeTransformationObject();
//            testContainer.warp.doUnidirectionalRegistration_Setup();
//
//            suceeded = testContainer.warp.doUnidirectionalRegistration_Optimization();
//        }



//        testContainer.warp.doUnidirectionalRegistration_Optimization(2);

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
                    Paths.get(testContainer.outputFolder, "optimization-errors.csv").toString(), false);

            TestHelper.saveTransformationCoeffs(testContainer.warp, testContainer.outputFolder,
                    "transformationCoeffs");

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    Paths.get(testContainer.outputFolder, "warped-source.csv").toString(),
                    false);

            TestHelper.saveArrayCSV(transformation_x[0],
                    Paths.get(testContainer.outputFolder, "raw-transformation-X.csv").toString(),
                    false);
            TestHelper.saveArrayCSV(transformation_y[0],
                    Paths.get(testContainer.outputFolder, "raw-transformation-Y.csv").toString(),
                    false);

        } else {

        }

    }




}