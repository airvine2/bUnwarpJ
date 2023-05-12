package bunwarpj;

import ij.IJ;
import ij.ImagePlus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class bUnwarpJ_Test {

    private Path resourcePath;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.resourcePath = Paths.get("", TestHelper.RESOURCES_DIR);
    }


    @Test
    /**
     * (2D data input) Test computeTransformation_Autotune,
     * which chooses the optimal starting and ending resolutions
     * error is measured by L2 pixel diff between the warped source image and the target image
     */
    void computeTransformation_Autotune_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        Path inputFolder = this.resourcePath.resolve("2D-int");

        TestContainer testContainer = new TestContainer(inputFolder.toString());

        //copied code from computeTransformationBatch_Autotune

        BSplineModel source = new BSplineModel(testContainer.sourceMtxInt, true);
        ImagePlus sourceImp = MiscTools.createImagePlusByte(testContainer.sourceMtxInt, "source image");
        Mask sourceMsk = new Mask(testContainer.sourceMtxInt[0].length, testContainer.sourceMtxInt.length);
        PointHandler sourcePh = new PointHandler(sourceImp);

        BSplineModel target = new BSplineModel(testContainer.targetMtxInt, true);
        ImagePlus targetImp = MiscTools.createImagePlusByte(testContainer.targetMtxInt, "target image");
        Mask targetMsk = new Mask(testContainer.targetMtxInt[0].length, testContainer.targetMtxInt.length);
        PointHandler targetPh = new PointHandler(targetImp);

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        Transformation warp =
                bUnwarpJ_.computeTransformation_Autotune(target, source, testContainer.options,
                        targetImp, sourceImp, targetMsk, sourceMsk,
                        targetPh, sourcePh, null, null,
                        testContainer.targetMtxInt, testContainer.sourceMtxInt, "both");


        int[] resolutionsUsed = new int[] {warp.getMin_scale_deformation(), warp.getMax_scale_deformation()};

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(warp, testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(warp,
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
    /**
     * (1D data input) Test computeTransformation_Autotune,
     * which chooses the optimal starting and ending resolutions
     * error is measured by L2 pixel diff between the warped source image and the target image
     */
    void computeTransformation_Autotune_1D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = true;

        String inputFolder = this.resourcePath.resolve(
                Paths.get("1D-bad-transform-autotune")).toString();

        TestContainer testContainer = new TestContainer(inputFolder);

        testContainer.options.setImageSumDecreaseThreshold(0.5);

        //copied code from computeTransformationBatch_Autotune

        BSplineModel source = new BSplineModel(testContainer.sourceMtxInt, true);
        ImagePlus sourceImp = MiscTools.createImagePlusByte(testContainer.sourceMtxInt, "source image");
        Mask sourceMsk = new Mask(testContainer.sourceMtxInt[0].length, testContainer.sourceMtxInt.length);
        PointHandler sourcePh = new PointHandler(sourceImp);

        BSplineModel target = new BSplineModel(testContainer.targetMtxInt, true);
        ImagePlus targetImp = MiscTools.createImagePlusByte(testContainer.targetMtxInt, "target image");
        Mask targetMsk = new Mask(testContainer.targetMtxInt[0].length, testContainer.targetMtxInt.length);
        PointHandler targetPh = new PointHandler(targetImp);

        testContainer.options.min_scale_deformation = 0;
        testContainer.options.max_scale_deformation = 4;

        Transformation warp =
                bUnwarpJ_.computeTransformation_Autotune(target, source, testContainer.options,
                        targetImp, sourceImp, targetMsk, sourceMsk,
                        targetPh, sourcePh, null, null,
                        testContainer.targetMtxInt, testContainer.sourceMtxInt, "both");


//        int[][] trainImgMtx = AAGTestUtils.import_CsvToMtxInt(this.resourcePath.resolve(trainFileName).toString());
//        int[][] targetImgMtx = AAGTestUtils.import_CsvToMtxInt(this.resourcePath.resolve(targetFileName).toString());
//
//        Transformation resultTransform = bUnwarpJ_.computeTransformationBatch(targetImgMtx, trainImgMtx,
//                options.getbUnwarpJ_options());

        int[] resolutionsUsed = new int[] {warp.getMin_scale_deformation(), warp.getMax_scale_deformation()};

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(warp, testContainer.sourceMtxInt);

        String baseFileName = "computeTransformation_Autotune_1D";
        String coeffsFile = baseFileName + "_coeffs";
        String warpedFile = baseFileName + "warped.csv";

        if (initResults) {

            TestHelper.saveTransformationCoeffs(warp,
                    testContainer.outputFolder, coeffsFile);

            TestHelper.saveArrayCSV(warpedImageMtx[0],
                    testContainer.outputFolder, warpedFile, false);

        } else {

            int[] expectedResolutions = new int[] {2,3};
            assertTrue(Arrays.equals(expectedResolutions, resolutionsUsed));

            TestHelper.compareTransformationCoeffsToFile(warp,
                    testContainer.outputFolder, coeffsFile);

            int[][] expectedWarpedImageMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(testContainer.outputFolder, warpedFile).toString()
            );
            assertTrue(Arrays.equals(warpedImageMtx[0], expectedWarpedImageMtx[0]));

        }

    }

    @Test
    /**
     * (2D data input) Compare regular computeTransformationBatch to computeTransformation_Autotune,
     * to make sure both functions give the same results if the same resolutions are used
     */
    void computeTransformation_AutotuneVSRegular_2D() throws Exception{

        //true if we are saving results, false if we are checking results against a file
        boolean initResults = false;

        Path inputFolder = this.resourcePath.resolve("2D-int");

        TestContainer testContainer = new TestContainer(inputFolder.toString());

        //copied code from computeTransformationBatch

        // Create source image model
        final BSplineModel source = new BSplineModel(testContainer.sourceMtxInt, true);
        ImagePlus sourceImp = MiscTools.createImagePlusByte(testContainer.sourceMtxInt, "source image");
        final Mask sourceMsk = new Mask(testContainer.sourceMtxInt[0].length, testContainer.sourceMtxInt.length);
        PointHandler sourcePh  = new PointHandler(sourceImp);

        // Create target image model
        BSplineModel target = new BSplineModel(testContainer.targetMtxInt, true);
        ImagePlus targetImp = MiscTools.createImagePlusByte(testContainer.targetMtxInt, "target image");
        Mask targetMsk = new Mask(testContainer.targetMtxInt[0].length, testContainer.targetMtxInt.length);
        PointHandler targetPh = new PointHandler(targetImp);

        testContainer.options.min_scale_deformation = 1;
        testContainer.options.max_scale_deformation = 4;

        final Transformation warp =  bUnwarpJ_.computeTransformation(target, source, testContainer.options,
                targetImp, sourceImp, targetMsk,
                sourceMsk, targetPh, sourcePh, null, null);

        int[] resolutionsUsed = new int[] {warp.getMin_scale_deformation(), warp.getMax_scale_deformation()};

        //apply transformation to source image
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(warp, testContainer.sourceMtxInt);

        if (initResults) {

            TestHelper.saveTransformationCoeffs(warp,
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

    @org.junit.jupiter.api.Test
    @DisplayName("test computeTransformationBatch")
    void computeTransformationBatch() {
    }

    @org.junit.jupiter.api.Test
    void testComputeTransformationBatch() {

    }

    @org.junit.jupiter.api.Test
    void testComputeTransformationBatch1() {
    }

    @org.junit.jupiter.api.Test
    void testComputeTransformationBatch2() {
    }

    @org.junit.jupiter.api.Test
    @DisplayName("test overloaded computeTransformationBatch(int[][] targetImageMtx, int[][] sourceImageMtx, Param parameter)")
    void testComputeTransformationBatch3() throws Exception {

        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");

        Transformation tResult = bUnwarpJ_.computeTransformationBatch(testContainer.targetMtxInt, testContainer.sourceMtxInt,
                testContainer.options);

        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(tResult, testContainer.sourceMtxInt);

        //initial saving the results
//        TestHelper.saveMtxAsPng(testContainer.sourceMtx, testContainer.outputFolder, "source-image");
//        TestHelper.saveMtxAsPng(testContainer.targetMtx, testContainer.outputFolder, "target-image");
        TestHelper.saveTransformationCoeffs(tResult, testContainer.outputFolder, "transformationCoeffs_coarse");
        TestHelper.saveMtxAsPng(warpedImageMtx, testContainer.outputFolder, "warped-source");

//        //comparing results against the saved results
//        int[][] sourceFromPng = TestHelper.import_PngToMtxInt(Paths.get(dataFolder,"results", "source-image.png").toString());
//        int[][] targetFromPng = TestHelper.import_PngToMtxInt(Paths.get(dataFolder,"results", "target-image.png").toString());
//
//        assertTrue(Arrays.deepEquals(sourceMtx, sourceFromPng));
//        assertTrue(Arrays.deepEquals(targetMtx, targetFromPng));
//        assertTrue(Arrays.deepEquals(warpedImageMtx, targetFromPng));

    }

}