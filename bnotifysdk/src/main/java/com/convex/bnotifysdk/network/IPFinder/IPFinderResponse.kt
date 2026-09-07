package com.techionic.customnotifcationapp.network.IPFinder

import com.google.gson.annotations.SerializedName

internal class IPFinderResponse {
    @SerializedName("data")
    var data: Data? = null

    class Data {
        @SerializedName("status")
        var status: Boolean = false
        @SerializedName("code")
        var code: Int = 0
        @SerializedName("message")
        var message: String? = null
        @SerializedName("data")
        var ip_data: IPData? = null

        class IPData {
            @SerializedName("country")
            var country: String? = null
            @SerializedName("country_code")
            var country_code: String? = null
            @SerializedName("ip_address")
            var ip_address: String? = null
            @SerializedName("latitude")
            var latitude: Double = 0.0
            @SerializedName("longitude")
            var longitude: Double = 0.0
        }
    }
}