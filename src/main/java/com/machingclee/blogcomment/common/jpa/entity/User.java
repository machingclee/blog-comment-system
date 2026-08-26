package com.machingclee.blogcomment.common.jpa.entity;

import com.machingclee.domain.util.annotation.BoundedContext;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.UUID;

@BoundedContext("Blog Comments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "comment_user", schema = "blog_system")
public class User {

    // region columns
    @Id
    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @ColumnDefault("ulid_as_uuid()")
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_email", nullable = false, unique = true)
    private String userEmail;

    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    /**
     * Google profile photo URL from the ID token `picture` claim (nullable).
     */
    @Column(name = "user_picture")
    private String userPicture;

    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_created_at()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Double createdAt;

    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_created_at_hk_timestr()")
    @Column(name = "created_at_hk", nullable = false, updatable = false)
    private String createdAtHk;
    // endregion

    /**
     * Factory for handlers (constructor is package-protected for JPA).
     * DB-generated fields (id/createdAt/createdAtHk) are filled on flush.
     */
    public static User create(String userEmail, String userName, String userPicture) {
        User u = new User();
        u.userEmail = userEmail;
        u.userName = userName;
        u.userPicture = userPicture;
        return u;
    }

    // region DTO — Style A (domain return shape, see the domain-util skill)
    @Data
    @Builder
    public static class DTO {
        private UUID id;
        private String userEmail;
        private String userName;
        private String userPicture;
        private Double createdAt;
        private String createdAtHk;
    }
    // endregion
}
