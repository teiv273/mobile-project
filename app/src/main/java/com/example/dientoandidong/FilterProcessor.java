package com.example.dientoandidong;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class FilterProcessor {

    public static Mat applyGrayscale(Mat src, double intensity) {
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(gray, gray, Imgproc.COLOR_GRAY2BGR);
        return blend(src, gray, intensity);
    }

    public static Mat applySepia(Mat src, double intensity) {
        Mat sepia = src.clone();
        Mat kernel = new Mat(4, 4, CvType.CV_32F);
        kernel.put(0, 0,
                0.272, 0.534, 0.131, 0,
                0.349, 0.686, 0.168, 0,
                0.393, 0.769, 0.189, 0,
                0, 0, 0, 1);
        Core.transform(sepia, sepia, kernel);
        return blend(src, sepia, intensity);
    }

    public static Mat applyInvert(Mat src, double intensity) {
        Mat invert = new Mat();
        Core.bitwise_not(src, invert);
        return blend(src, invert, intensity);
    }

    private static Mat blend(Mat src1, Mat src2, double alpha) {
        Mat result = new Mat();
        Core.addWeighted(src1, 1.0 - alpha, src2, alpha, 0.0, result);
        return result;
    }
}
