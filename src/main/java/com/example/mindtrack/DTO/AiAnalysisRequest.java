package com.example.mindtrack.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiAnalysisRequest {

    private String imageUrl;

    private int visitCnt;
}
