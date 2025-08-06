package com.example.mindtrack.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
public class SimilarityCheckService {

    // WIDTH, HEIGHT 값을 조정하면, 해시 민감도를 조절할 수 있음
    private static final int WIDTH = 17;
    private static final int HEIGHT = 16;
    private static final int threshold = 5;

    private static final double C1 = 6.5025;
    private static final double C2 = 58.5225;

    // dHash(차분해시)
    // 1. 입력 이미지를 고정된 크기(WIDTH X HEIGHT)의 그레이스케일 이미지로 리사이즈
    // 2. 각 행(row)마다 인접한 픽셀(왼족과 오른쪽)의 밝기값(그레이스케일)을 비교
    // 3. 왼쪽 픽셀이 더 밝으면 1, 그렇지 않으면 0으로 해시 비트로 추가
    // 4. 이렇게 생성된 이진 문자열이 이미지의 dHash 값이 됨
    // 이미지의 전체적인 구조(윤곽, 형태)에 민감
    // 이미지가 약간 변경되어도 유사한 해시 값을 갖도록 되어있음
    // 이전에 저장된 적있는 유사한 이미지인지 비교하기 위해 있음
    public long computeHash(BufferedImage img) {
        BufferedImage resized = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(img, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();

        long hash = 0L;
        int bitIndex = 0;

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH - 1; x++) {
                int left = new Color(resized.getRGB(x, y)).getRed();
                int right = new Color(resized.getRGB(x + 1, y)).getRed();

                if (left - right > threshold) {
                    hash |= (1L << bitIndex); // 해당 위치 비트 ON
                }
                bitIndex++;
            }
        }

        return hash;
    }


    /**
     * SSIM(Structural Similarity Index) 공식으로 유사도 비교
     * - 두 이미지의 밝기, 대비, 구조 정보를 바탕으로 유사도 측정
     * - 값의 범위: -1 ~ 1(보통 0~1), 1에 가까울 수록 이미지가 유사함을 의미
     * @param img1
     * @param img2
     * @return SSIM 유사도 (double)
     */
    public double computeSimilarity(BufferedImage img1, BufferedImage img2) {
        // 1. 같은 크기로 변환 (리사이즈)
        int width = 256;
        int height = 256;

        BufferedImage gray1 = resizeAndGrayscale(img1, width, height);
        BufferedImage gray2 = resizeAndGrayscale(img2, width, height);

        // 2. 각 이미지 픽셀 배열 생성
        double[][] pixels1 = getLuminanceMatrix(gray1);
        double[][] pixels2 = getLuminanceMatrix(gray2);

        // 3. 평균 밝기
        // 이미지의 전체적인 밝기 수준을 계산
        double mu1 = mean(pixels1);
        double mu2 = mean(pixels2);

        // 4. 분산
        // 픽셀 값의 퍼짐 정도(대비)
        // (각 픽셀 - 평균)^2의 평균값 (평균에서 각 픽셀들이 얼마나 떨어져있는지)
        double sigma1Sq = variance(pixels1, mu1);
        double sigma2Sq = variance(pixels2, mu2);

        // 5. 공분산
        // 두 이미지가 같은 위치에서 얼마나 함께 밝거나 어두운지 (구조적 유사성 측정)
        double sigma12 = covariance(pixels1, pixels2, mu1, mu2);

        // 6. SSIM 계산 공식은 .. 인터넷에서 보고~ 그냥 그렇구나~ 이해 불가
        double numerator = (2 * mu1 * mu2 + C1) * (2 * sigma12 + C2);
        double denominator = (mu1 * mu1 + mu2 * mu2 + C1) * (sigma1Sq + sigma2Sq + C2);

        return numerator / denominator;
    }

    /**
     * 입력 이미지를 지정된 크기의 그레이스케일로 변환
     * @param original
     * @param width
     * @param height
     * @return
     */
    private BufferedImage resizeAndGrayscale(BufferedImage original, int width, int height){
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * 이미지로부터 밝기 값을 추출하여 2차원 배열로 반환
     * @param img
     * @return
     */
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

    /**
     * 2차원 밝기 배열의 평균값 계산
     * 이미지 전체의 평균 밝기를 나타냄
     * @param matrix
     * @return
     */
    private double mean(double[][] matrix){
        double sum = 0;
        for(double[] row: matrix){
            for(double v: row){
                sum += v;
            }
        }
        return sum / (matrix.length * matrix[0].length);
    }

    /**
     * 2차원 밝기 배열의 분산 계산
     * 각 픽셀이 평균에서 얼마나 떨어져 있는지를 제곱하여 평균냄
     * @param matrix
     * @param mean
     * @return
     */
    private double variance(double[][] matrix, double mean){
        double sum = 0;
        for(double[] row: matrix){
            for(double v : row){
                sum += (v-mean) * (v-mean); // 크기만 비교하기 위해 제곱 처리
            }
        }
        return sum / (matrix.length * matrix[0].length);
    }

    /**
     * 두 이미지 간의 공분산 계산
     * 같은 위치의 픽셀이 함께 밝거나 어두운 경향을 나타냄
     * 구조적 유사성(패턴 유사도)를 측정하는 데 사용됨
     * @param m1
     * @param m2
     * @param mean1
     * @param mean2
     * @return
     */
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
    public int hammingDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
    }

    public double similarity(long h1, long h2) {
        int bitLength = (WIDTH - 1) * HEIGHT;
        return 1.0 - (double) Long.bitCount(h1 ^ h2) / bitLength;
    }
}
