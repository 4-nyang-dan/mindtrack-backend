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
        // 1. 다운스케일 크기 설정
        int width = 128;
        int height = 128;

        // 2. 그레이스케일 변환 및 리사이즈
        BufferedImage gray1 = resizeAndGrayscale(img1, width, height);
        BufferedImage gray2 = resizeAndGrayscale(img2, width, height);

        // 3. SSIM을 타일 단위로 계산
        int tileCountX = 4;
        int tileCountY = 4;
        int tileW = width / tileCountX;
        int tileH = height / tileCountY;

        double totalSsim = 0.0;
        int tileNum = 0;

        for (int ty = 0; ty < tileCountY; ty++) {
            for (int tx = 0; tx < tileCountX; tx++) {
                // ROI(Region of Interest) 영역 지정
                int startX = tx * tileW;
                int startY = ty * tileH;

                double[][] pixels1 = getLuminanceMatrix(gray1, startX, startY, tileW, tileH);
                double[][] pixels2 = getLuminanceMatrix(gray2, startX, startY, tileW, tileH);

                // 타일별 평균/분산/공분산 계산
                double mu1 = mean(pixels1);
                double mu2 = mean(pixels2);
                double sigma1Sq = variance(pixels1, mu1);
                double sigma2Sq = variance(pixels2, mu2);
                double sigma12 = covariance(pixels1, pixels2, mu1, mu2);

                double numerator = (2 * mu1 * mu2 + C1) * (2 * sigma12 + C2);
                double denominator = (mu1 * mu1 + mu2 * mu2 + C1) * (sigma1Sq + sigma2Sq + C2);
                if (denominator == 0) {
                    return 0; // 계산 불가능한 경우 0으로 처리
                }
                double ssim = numerator / denominator;


                totalSsim += ssim;
                tileNum++;
            }
        }

        // 4. 전체 타일 SSIM 평균 반환
        return totalSsim / tileNum;
    }

    /**
     * 이미지의 특정 영역(ROI)을 2차원 밝기 배열로 변환
     * @param img 대상 이미지
     * @param startX 영역 시작 X좌표
     * @param startY 영역 시작 Y좌표
     * @param w 영역 폭
     * @param h 영역 높이
     * @return 선택 영역의 밝기값 행렬
     */
    private double[][] getLuminanceMatrix(BufferedImage img, int startX, int startY, int w, int h) {
        int endX = Math.min(startX + w, img.getWidth());
        int endY = Math.min(startY + h, img.getHeight());
        int realW = endX - startX;
        int realH = endY - startY;

        double[][] matrix = new double[realH][realW];
        for (int y = 0; y < realH; y++) {
            for (int x = 0; x < realW; x++) {
                int rgb = img.getRGB(startX + x, startY + y) & 0xFF;
                matrix[y][x] = rgb;
            }
        }
        return matrix;
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
