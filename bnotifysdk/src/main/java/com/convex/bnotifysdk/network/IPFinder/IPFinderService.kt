package com.techionic.customnotifcationapp.network.IPFinder

import retrofit2.Call
import retrofit2.http.GET

internal interface IPFinderService {

    @GET("ipdetails")
    fun getPublicIpAddress(): Call<IPFinderResponse>
}