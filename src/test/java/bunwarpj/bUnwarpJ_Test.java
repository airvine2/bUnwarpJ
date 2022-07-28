package bunwarpj;

import org.junit.jupiter.api.DisplayName;

class bUnwarpJ_Test {

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
//        TestHelper.saveMtxAsPng(testContainer.sourceMtx, "source-image", testContainer.outputFolder);
//        TestHelper.saveMtxAsPng(testContainer.targetMtx, "target-image", testContainer.outputFolder);
        TestHelper.saveTransformationCoeffs(tResult, testContainer.outputFolder, "transformationCoeffs_coarse");
        TestHelper.saveMtxAsPng(warpedImageMtx, "warped-source", testContainer.outputFolder);

//        //comparing results against the saved results
//        int[][] sourceFromPng = TestHelper.import_PngToMtxInt(Paths.get(dataFolder,"results", "source-image.png").toString());
//        int[][] targetFromPng = TestHelper.import_PngToMtxInt(Paths.get(dataFolder,"results", "target-image.png").toString());
//
//        assertTrue(Arrays.deepEquals(sourceMtx, sourceFromPng));
//        assertTrue(Arrays.deepEquals(targetMtx, targetFromPng));
//        assertTrue(Arrays.deepEquals(warpedImageMtx, targetFromPng));

    }

    @org.junit.jupiter.api.Test
    void computeTransformation() throws Exception {
//        TestContainer testContainer = new TestContainer("C:\\images-as-csv\\2D-int");
//
//        Transformation tResult = bUnwarpJ_.computeTransformationBatch(testContainer.targetMtx, testContainer.sourceMtx,
//                testContainer.options);
//
//        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(tResult, testContainer.sourceMtx);
//


    }
}