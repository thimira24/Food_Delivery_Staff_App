package com.development.app.remote


import retrofit2.http.GET
import retrofit2.http.Query
import io.reactivex.Observable

interface IGoogleApi {
    @GET("maps/api/directions/json")
    fun getDirections(
        @Query("mode") mode: String?,
        @Query("transit_routing_preference") transit_routing: String?,
        @Query("origin") origin: String?,
        @Query("destination") destination: String?,
        @Query("key") key: String?
    ): Observable<String?>?

}