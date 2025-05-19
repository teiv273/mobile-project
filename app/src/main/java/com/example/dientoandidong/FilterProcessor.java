package com.example.dientoandidong;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
// Import org.opencv.photo.Photo; // Bỏ comment nếu phiên bản OpenCV của bạn có stylization trong Photo

public class FilterProcessor {

    public static Mat applyGrayscale(Mat src, double intensity) {
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(gray, gray, Imgproc.COLOR_GRAY2BGR);
        Mat result = blend(src, gray, intensity);
        gray.release();
        return result;
    }

    public static Mat applySepia(Mat src, double intensity) {
        Mat sepiaMat = src.clone();
        Mat kernel = new Mat(4, 4, CvType.CV_32F);
        kernel.put(0, 0,
                0.272, 0.534, 0.131, 0,
                0.349, 0.686, 0.168, 0,
                0.393, 0.769, 0.189, 0,
                0, 0, 0, 1);
        Core.transform(sepiaMat, sepiaMat, kernel);
        Mat result = blend(src, sepiaMat, intensity);
        kernel.release();
        sepiaMat.release();
        return result;
    }

    public static Mat applyInvert(Mat src, double intensity) {
        Mat invert = new Mat();
        Core.bitwise_not(src, invert);
        Mat result = blend(src, invert, intensity);
        invert.release();
        return result;
    }

    public static Mat applySwirl(Mat src, double intensity) {
        Mat swirled = new Mat();
        Mat mapX = new Mat(src.size(), CvType.CV_32FC1);
        Mat mapY = new Mat(src.size(), CvType.CV_32FC1);

        float centerX = src.cols() / 2.0f;
        float centerY = src.rows() / 2.0f;
        float maxStrength = (float) Math.min(src.cols(), src.rows()) / 4f;

        for (int y = 0; y < src.rows(); y++) {
            for (int x = 0; x < src.cols(); x++) {
                float dx = x - centerX;
                float dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                double swirlFactor = 1 - (distance / Math.max(centerX,centerY));
                if (swirlFactor < 0) swirlFactor = 0;
                double swirlAngle = maxStrength * swirlFactor * 0.1;
                float newX = (float) (centerX + distance * Math.cos(angle + swirlAngle));
                float newY = (float) (centerY + distance * Math.sin(angle + swirlAngle));
                mapX.put(y, x, newX);
                mapY.put(y, x, newY);
            }
        }
        Imgproc.remap(src, swirled, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, new Scalar(0,0,0));
        mapX.release();
        mapY.release();
        Mat result = blend(src, swirled, intensity);
        swirled.release();
        return result;
    }

    public static Mat applyWatercolor(Mat src, double intensity) {
        Mat watercolorEffect = new Mat();
        Mat temp = new Mat();

        // Bước 1: Làm mịn ảnh nhưng giữ lại cạnh bằng Bilateral Filter
        // Các tham số d, sigmaColor, sigmaSpace có thể cần điều chỉnh để có hiệu ứng tốt nhất
        // d: Đường kính của vùng lân cận pixel. Nếu là số âm, OpenCV sẽ tính từ sigmaSpace.
        // sigmaColor: Filter sigma trong không gian màu. Giá trị lớn hơn có nghĩa là các màu xa hơn trong vùng lân cận sẽ được trộn lẫn với nhau.
        // sigmaSpace: Filter sigma trong không gian tọa độ. Giá trị lớn hơn có nghĩa là các pixel ở xa hơn sẽ ảnh hưởng đến nhau miễn là màu của chúng đủ gần.
        Imgproc.bilateralFilter(src, temp, 15, 80, 80); // Ví dụ: d=15, sigmaColor=80, sigmaSpace=80

        // Bước 2: Giảm nhiễu và làm mịn thêm bằng Median Blur
        // ksize: Kích thước kernel, phải là số lẻ dương.
        Imgproc.medianBlur(temp, watercolorEffect, 7); // Ví dụ: ksize=7

        // (Tùy chọn) Nếu bạn muốn thử Photo.stylization và phiên bản OpenCV của bạn hỗ trợ:
        // try {
        //    Photo.stylization(src, watercolorEffect, 60, 0.45f);
        // } catch (UnsatisfiedLinkError | NoSuchMethodError e) {
        //    // Fallback nếu stylization không có hoặc lỗi
        //    Imgproc.bilateralFilter(src, temp, 15, 80, 80);
        //    Imgproc.medianBlur(temp, watercolorEffect, 7);
        // }

        temp.release();
        Mat result = blend(src, watercolorEffect, intensity);
        watercolorEffect.release();
        return result;
    }

    public static Mat applyNoise(Mat src, double intensity) {
        Mat noisyImage = src.clone();
        Mat noise = new Mat(src.size(), src.type());
        double stddev = 30.0;
        Core.randn(noise, 0.0, stddev);
        Core.add(noisyImage, noise, noisyImage);
        noisyImage.convertTo(noisyImage, -1);
        Mat result = blend(src, noisyImage, intensity);
        noisyImage.release();
        noise.release();
        return result;
    }

    private static Mat blend(Mat src1, Mat src2, double alpha) {
        Mat result = new Mat();
        Core.addWeighted(src1, 1.0 - alpha, src2, alpha, 0.0, result);
        return result;
    }
}