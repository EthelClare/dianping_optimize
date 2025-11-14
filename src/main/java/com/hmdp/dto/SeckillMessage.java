package com.hmdp.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMessage implements Serializable {

    private String userId;
    private String orderId;
    private String eventId;
    private Long voucherId;
}
