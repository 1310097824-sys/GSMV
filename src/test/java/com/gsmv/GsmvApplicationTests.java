package com.gsmv;

import static org.assertj.core.api.Assertions.assertThat;

import com.gsmv.common.ApiResponse;
import org.junit.jupiter.api.Test;

class GsmvApplicationTests {

    @Test
    void apiResponseShouldWrapSuccessPayload() {
        ApiResponse<String> response = ApiResponse.success("ok");
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("ok");
    }
}
