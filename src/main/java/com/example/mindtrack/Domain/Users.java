package com.example.mindtrack.Domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Entity
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // ✅ PK (내부 시스템 참조용)

    @Column(unique = true, nullable = false)
    private String userId;  // ✅ 사용자 ID (로그인, 사용자 구분용)

    private String email;

    private String password;

    @OneToMany(mappedBy = "user")
    private List<ScreenshotImage> screenshotImages;
}
