package bunwarpj;

import ij.IJ;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

class BSplineModelTest {

    /**
     * set to true if we are saving results to file, false if we are checking results against a file
     */
    private final boolean OVERWRITE_RESULTS_FILES = false;
    private Path resourcePath;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.resourcePath = Paths.get("", TestHelper.RESOURCES_DIR);
    }

    @Test
    void buildBSplineModel() throws Exception {

        Path inputFolder = this.resourcePath.resolve("test data_1D_BsplineModel");

        TestContainer testContainer = new TestContainer(inputFolder.toString());

        testContainer.loadUnscaledFloatCsvData(inputFolder.toString(), "source.csv", "target.csv");

        BSplineModel bsModel = new BSplineModel(testContainer.sourceMtxFloat, true);

        final int imagePyramidDepth = testContainer.options.max_scale_deformation -
                testContainer.options.min_scale_deformation + 1;
        final int min_scale_image = 0;

        //calculate the BSpline model coefficients
        bsModel.setPyramidDepth(imagePyramidDepth + min_scale_image);
        bsModel.startPyramids();

        // Join threads
        bsModel.getThread().join();

        if (OVERWRITE_RESULTS_FILES) {
            //write out coeffs at every level of pyramid
            savePyramidArrays(bsModel.getCpyramid(), testContainer.outputFolder, "coefficientsPyramid");
            savePyramidArrays(bsModel.getImgpyramid(), testContainer.outputFolder, "imagePyramid");
        } else {
            //check coeffs
            List<double[]> expectedCoeffs = TestHelper.import_CsvToList(Paths.get(testContainer.outputFolder,
                    "coefficientsPyramid.csv").toString());
            List<double[]> actualCoeffs = getPyramidArrays(bsModel.getCpyramid());
            assertTrue(TestHelper.compareListOfArrays(expectedCoeffs, actualCoeffs));

            List<double[]> expectedImagePyramid = TestHelper.import_CsvToList(Paths.get(testContainer.outputFolder,
                    "imagePyramid.csv").toString());
            List<double[]> actualImagePyramid = getPyramidArrays(bsModel.getImgpyramid());
            assertTrue(TestHelper.compareListOfArrays(expectedImagePyramid, actualImagePyramid));
        }

    }

    private static void savePyramidArrays(Stack<Object> pyramid, String outputFolder, String outFileName) {
        List<double[]> coefficientPyramidList = getPyramidArrays(pyramid);
        TestHelper.saveArrayCSV(coefficientPyramidList, Paths.get(outputFolder, outFileName).toString());
    }

    private static List<double[]> getPyramidArrays(Stack<Object> pyramid) {

        int numLevels = pyramid.size() / 3;

        List<double[]> stackArrays = new ArrayList<>();

        int itemCntr = 0;
        for (int i=0; i<numLevels; i++) {
            int currentWidth       = ((Integer)TestHelper.getStackElement(pyramid, itemCntr)).intValue();
            int currentHeight      = ((Integer)TestHelper.getStackElement(pyramid, itemCntr+1)).intValue();
            double[] currentCoefficient = (double [])TestHelper.getStackElement(pyramid, itemCntr+2);
            itemCntr = itemCntr + 3;
            stackArrays.add(currentCoefficient);
        }

        return(stackArrays);
    }

}