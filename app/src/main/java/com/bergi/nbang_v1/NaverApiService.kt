package com.bergi.nbang_v1

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NaverApiService {
    @GET("map-reversegeocode/v2/gc")
    fun reverseGeocode(
        @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
        @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
        @Query("coords") coords: String,
        @Query("orders") orders: String = "addr",
        @Query("output") output: String = "json"
    ): Call<ReverseGeocodingResponse>
}

data class ReverseGeocodingResponse(
    val results: List<AddressInfo>
)

data class AddressInfo(
    val name: String,
    val region: Region,
    val land: Land
)

data class Region(
    val area0: Area,
    val area1: Area,
    val area2: Area,
    val area3: Area,
    val area4: Area
)

data class Land(
    val type: String,
    val number1: String,
    val number2: String,
    val addition0: Addition,
    val addition1: Addition,
    val addition2: Addition,
    val addition3: Addition,
    val addition4: Addition,
    val name: String,
    val coords: Coords
)

data class Area(
    val name: String,
    val coords: Coords
)

data class Coords(
    val center: Center
)

data class Center(
    val crs: String,
    val x: Double,
    val y: Double
)

data class Addition(
    val type: String,
    val value: String
)