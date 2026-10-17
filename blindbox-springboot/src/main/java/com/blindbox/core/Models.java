package com.blindbox.core;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class Models {
    private Models() {}
    @Data public static class User {
        private Integer id; private String account; private String password;
        private Long balance; private String role; private LocalDateTime createdAt;
    }
    @Data public static class Box {
        private Integer id; private String name; private String series; private String image;
        private String description; private Long price; private Integer stock; private String status;
        @com.fasterxml.jackson.annotation.JsonIgnore private Integer creatorId;
        @com.fasterxml.jackson.annotation.JsonIgnore private String createRequestId;
        @com.fasterxml.jackson.annotation.JsonIgnore private String createPayloadHash;
    }
    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class CatalogRow extends Box {
        private Integer prizeId; private String prizeName; private String prizeImg;
        private String rarity; private Integer prizeStock; private Integer prizeValue;
    }
    @Data public static class BoxStock {
        private Integer id; private Integer stock; private String status; private Long price;
    }
    @Data public static class Prize {
        private Integer id; private Integer blindBoxId; private String prizeName; private String prizeImg;
        private String rarity; private Integer stock; private Integer value;
    }
    @Data public static class Order {
        private Integer id; private Integer userId; private Integer blindBoxId; private Integer prizeId;
        private String requestId; private Long price; private String status; private LocalDateTime createdAt;
        private String blindBoxName; private String prizeName; private String prizeImg; private String rarity;
    }
    @Data public static class StockReceipt { private Integer prizeId; private Integer delta; }
    @Data public static class Wallet {
        private Long id; private Long amount; private Long balanceAfter;
    }
    @Data public static class Show {
        private Integer id; private Integer userId; private String content; private String images;
        private Integer likes; private String username; private Boolean liked; private LocalDateTime createdAt;
    }
    public record Credentials(@NotBlank @Size(min=3,max=50) String account,
                              @NotBlank @Size(min=8,max=64) String password) {}
    public record LoginCredentials(@NotBlank @Size(max=50) String account,
                                   @NotBlank @Size(max=64) String password) {}
    public record Recharge(@NotNull @Min(1) @Max(10000000) Long amount) {}
    public record PrizeInput(@NotBlank @Size(max=100) String prizeName,
        @NotBlank @Size(max=255) String prizeImg,
        @Pattern(regexp="common|rare|secret") @NotNull String rarity,
        @NotNull @Min(0) @Max(1000000) Integer stock,
        @NotNull @Min(0) @Max(1000000) Integer value) {}
    public record CreateBox(@NotBlank @Size(max=100) String name,
        @Pattern(regexp="sea|forest|space|candy") @NotNull String series,
        @NotBlank @Size(max=255) String image, @Size(max=500) String description,
        @NotNull @Min(1) @Max(10000000) Long price,
        @NotEmpty @Size(max=50) List<@NotNull @Valid PrizeInput> prizes) {}
    public record UpdateBox(@NotBlank @Size(max=100) String name,
        @Pattern(regexp="sea|forest|space|candy") @NotNull String series,
        @NotBlank @Size(max=255) String image,@Size(max=500) String description,
        @NotNull @Min(1) @Max(10000000) Long price) {}
    public record Status(@Pattern(regexp="DRAFT|ON_SALE|OFF_SALE") @NotNull String status) {}
    public record Restock(@NotNull @Min(1) Integer prizeId,
                          @NotNull @Min(1) @Max(1000000) Integer quantity) {}
    public record Publish(@NotBlank @Size(max=2000) String content,
                          @Size(max=6) List<@NotBlank @Size(max=255) String> images) {}
    public record Comment(@NotBlank @Size(max=1000) String content) {}
}
