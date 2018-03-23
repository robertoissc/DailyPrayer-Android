package com.isscroberto.dailyprayerandroid.data.source;

import com.isscroberto.dailyprayerandroid.data.source.retrofit.PrayerApi;
import com.isscroberto.dailyprayerandroid.data.models.RssResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

/**
 * Created by roberto.orozco on 11/09/2017.
 */

@Singleton
public class PrayerRemoteDataSource implements BaseDataSource<RssResponse> {

    @Inject
    public PrayerRemoteDataSource(){}

    @Override
    public void get(final Callback<RssResponse> callback) {
        Retrofit retrofit = new Retrofit.Builder().baseUrl("http://www.plough.com/").addConverterFactory(SimpleXmlConverterFactory.create()).build();
        PrayerApi api = retrofit.create(PrayerApi.class);
        Call<RssResponse> apiCall = api.get();
        apiCall.enqueue(callback);
    }

}
