package com.example.mindtrack.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
public class SimilarityCheckService {

    private static final int WIDTH = 17;
    private static final int HEIGHT = 16;

    private static final double C1 = 6.5025;
    private static final double C2 = 58.5225;

    // 해시 계산
    public String computeHash(BufferedImage img) {
        BufferedImage resized = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(img, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();

        StringBuilder hash = new StringBuilder();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH - 1; x++) {
                int left = new Color(resized.getRGB(x, y)).getRed();
                int right = new Color(resized.getRGB(x + 1, y)).getRed();
                hash.append(left > right ? "1" : "0");
            }
        }
        return hash.toString();
    }

    public double computeSimilarity(BufferedImage img1, BufferedImage img2) {
        // 1. 같은 크기로 변환 (리사이즈)
        int width = 256;
        int height = 256;

        BufferedImage gray1 = resizeAndGrayscale(img1, width, height);
        BufferedImage gray2 = resizeAndGrayscale(img2, width, height);

        // 2. 픽셀 배열 생성
        double[][] pixels1 = getLuminanceMatrix(gray1);
        double[][] pixels2 = getLuminanceMatrix(gray2);

        // 3. 평균
        double mu1 = mean(pixels1);
        double mu2 = mean(pixels2);

        // 4. 분산
        double sigma1Sq = variance(pixels1, mu1);
        double sigma2Sq = variance(pixels2, mu2);

        // 5. 공분산
        double sigma12 = covariance(pixels1, pixels2, mu1, mu2);

        // 6. SSIM 계산
        double numerator = (2 * mu1 * mu2 + C1) * (2 * sigma12 + C2);
        double denominator = (mu1 * mu1 + mu2 * mu2 + C1) * (sigma1Sq + sigma2Sq + C2);

        return numerator / denominator;
    }

    private BufferedImage resizeAndGrayscale(BufferedImage original, int width, int height){
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private double[][] getLuminanceMatrix(BufferedImage img){
        int width = img.getWidth();
        int height = img.getHeight();
        double[][] matrix = new double[height][width];

        for(int y = 0; y < height;y++){
            for(int x = 0; x < width; x++){
                int rgb = img.getRGB(x, y) & 0xFF;
                matrix[y][x] = rgb;
            }
        }
        return matrix;
    }

    private double mean(double[][] matrix){
        double sum = 0;
        for(double[] row: matrix){
            for(double v: row){
                sum += v;
            }
        }
        return sum / (matrix.length * matrix[0].length);
    }

    private double variance(double[][] matrix, double mean){
        double sum = 0;
        for(double[] row: matrix){
            for(double v : row){
                sum += (v-mean) * (v-mean);
            }
        }
        return sum / (matrix.length * matrix[0].length);
    }

    private double covariance(double[][] m1, double[][] m2, double mean1, double mean2){
        double sum = 0;
        for(int y = 0; y < m1.length;y++){
            for(int x = 0; x< m1[0].length;x++){
                sum += (m1[y][x] - mean1) * (m2[y][x] - mean2);
            }
        }
        return sum / (m1.length * m1[0].length);
    }

    // Hamming distance로 유사도 측정
    public double computeSimilarityByHash(String hash1, String hash2) {
        int dist = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) dist++;
        }
        return 1.0 - (double) dist / hash1.length(); // 1.0: 완전 동일
    }
}
