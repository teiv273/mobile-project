package com.example.dientoandidong;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ImageFilters {

    public static Bitmap applySketchFilter(Bitmap input) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);

        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

        Mat inv = new Mat();
        Core.bitwise_not(gray, inv);

        Mat blur = new Mat();
        Imgproc.GaussianBlur(inv, blur, new Size(21, 21), 0);

// Tạo một ma trận 255 cùng kích thước với blur
        Mat white = new Mat(blur.size(), blur.type(), new Scalar(255));

// subtract(white - blur)
        Mat diff = new Mat();
        Core.subtract(white, blur, diff);

// gray / (255 - blur)
        Mat blend = new Mat();
        Core.divide(gray, diff, blend, 256.0);


        Imgproc.cvtColor(blend, blend, Imgproc.COLOR_GRAY2BGR);

        Bitmap result = Bitmap.createBitmap(blend.cols(), blend.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(blend, result);
        return result;
    }

    public static Bitmap applyEmbossFilter(Bitmap input) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);

        Mat kernel = new Mat(3, 3, CvType.CV_32F);
        kernel.put(0, 0,
                -2, -1, 0,
                -1, 1, 1,
                0, 1, 2
        );

        Mat output = new Mat();
        Imgproc.filter2D(img, output, -1, kernel);

        Bitmap result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, result);
        return result;
    }

    public static Bitmap applyVignetteFilter(Bitmap input, float radius, float strength) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);

        // Convert RGBA to BGR if needed
        if (img.channels() == 4) {
            Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR);
        }

        img.convertTo(img, CvType.CV_32F, 1.0 / 255.0);  // Normalize to [0,1]

        int width = img.cols();
        int height = img.rows();
        Point center = new Point(width / 2.0, height / 2.0);

        // Create distance mask
        Mat mask = new Mat(height, width, CvType.CV_32F);
        double maxDist = Math.sqrt(center.x * center.x + center.y * center.y);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                double dist = Math.sqrt(Math.pow(j - center.x, 2) + Math.pow(i - center.y, 2));
                double norm = dist / (maxDist * radius);
                float value = (float) Math.pow(1.0 - Math.min(1.0, norm), strength);
                mask.put(i, j, value);
            }
        }

        // Smooth mask with Gaussian Blur
        Imgproc.GaussianBlur(mask, mask, new Size(151, 151), 0);

        // Create 3-channel mask
        List<Mat> channels = new ArrayList<>();
        for (int i = 0; i < 3; i++) channels.add(mask);
        Mat mask3 = new Mat();
        Core.merge(channels, mask3);

        // Simply multiply the image by the mask - darker areas will go to black
        Mat result = new Mat();
        Core.multiply(img, mask3, result);

        // Convert back to 8-bit image
        result.convertTo(result, CvType.CV_8UC3, 255.0);

        Bitmap resultBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, resultBitmap);
        return resultBitmap;
    }

    public enum BlockShape {
        SQUARE, TRIANGLE, HEXAGON
    }

    public static Bitmap applyPixelateFilter(Bitmap inputBitmap, int blockSize, BlockShape shape) {
        Mat src = new Mat();
        Utils.bitmapToMat(inputBitmap, src);
        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB);

        Mat output = Mat.zeros(src.size(), src.type());

        if (shape == BlockShape.SQUARE) {
            for (int y = 0; y < src.rows(); y += blockSize) {
                for (int x = 0; x < src.cols(); x += blockSize) {
                    int width = Math.min(blockSize, src.cols() - x);
                    int height = Math.min(blockSize, src.rows() - y);
                    Rect rect = new Rect(x, y, width, height);
                    Mat block = src.submat(rect);
                    Scalar color = Core.mean(block);
                    Imgproc.rectangle(output, new Point(x, y), new Point(x + width, y + height), color, -1);
                }
            }
        }

        // TRIANGLE pixelation
        else if (shape == BlockShape.TRIANGLE) {
            for (int y = 0; y < src.rows(); y += blockSize) {
                for (int x = 0; x < src.cols(); x += blockSize) {
                    int width = Math.min(blockSize, src.cols() - x);
                    int height = Math.min(blockSize, src.rows() - y);
                    Rect rect = new Rect(x, y, width, height);

                    // Kiểm tra kích thước hợp lệ
                    if (width <= 0 || height <= 0) continue;

                    Mat block = src.submat(rect);
                    Scalar color = Core.mean(block);

                    // Triangle pointing down
                    Point[] triangle1 = new Point[]{
                            new Point(x, y),
                            new Point(x + width, y),
                            new Point(x + width / 2.0, y + height)
                    };

                    // Triangle pointing up
                    Point[] triangle2 = new Point[]{
                            new Point(x, y + height),
                            new Point(x + width, y + height),
                            new Point(x + width / 2.0, y)
                    };

                    // Giới hạn tọa độ trong ảnh
                    if (inImageBounds(triangle1, src.cols(), src.rows())) {
                        Imgproc.fillConvexPoly(output, new MatOfPoint(triangle1[0], triangle1[1], triangle1[2]), color);
                    }
                    if (inImageBounds(triangle2, src.cols(), src.rows())) {
                        Imgproc.fillConvexPoly(output, new MatOfPoint(triangle2[0], triangle2[1], triangle2[2]), color);
                    }
                }
            }
        }

// HEXAGON pixelation
        else if (shape == BlockShape.HEXAGON) {
            double hexHeight = blockSize;
            double hexWidth = Math.sqrt(3) / 2 * hexHeight;
            double vertSpacing = hexHeight * 3.0 / 4.0;

            for (double y = 0; y < src.rows(); y += vertSpacing) {
                boolean offset = ((int) (y / vertSpacing)) % 2 == 1;
                for (double x = offset ? hexWidth / 2 : 0; x < src.cols(); x += hexWidth) {
                    Point[] hexagon = new Point[6];
                    for (int i = 0; i < 6; i++) {
                        double angle = Math.toRadians(60 * i);
                        hexagon[i] = new Point(
                                x + hexWidth / 2 * Math.cos(angle),
                                y + hexHeight / 2 * Math.sin(angle)
                        );
                    }

                    // Kiểm tra vùng ảnh chứa hexagon
                    Rect bound = Imgproc.boundingRect(new MatOfPoint(hexagon));
                    int x1 = Math.max(bound.x, 0);
                    int y1 = Math.max(bound.y, 0);
                    int w = Math.min(bound.width, src.cols() - x1);
                    int h = Math.min(bound.height, src.rows() - y1);
                    if (w <= 0 || h <= 0) continue;

                    Mat block = src.submat(new Rect(x1, y1, w, h));
                    Scalar color = Core.mean(block);

                    Imgproc.fillConvexPoly(output, new MatOfPoint(hexagon), color);
                }
            }
        }


        Bitmap result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, result);
        return result;
    }


    private static boolean inImageBounds(Point[] points, int width, int height) {
        for (Point p : points) {
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height) return false;
        }
        return true;
    }

}


