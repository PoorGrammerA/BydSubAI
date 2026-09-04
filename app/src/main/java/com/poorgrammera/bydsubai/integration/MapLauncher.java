package com.poorgrammera.bydsubai.integration;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class MapLauncher {

    /**
     * 네이버 지도 앱을 실행하여 목적지로 즉시 실시간 내비게이션 주행을 시작합니다.
     * 출발지는 자동으로 사용자의 '현재 위치'로 지정됩니다.
     *
     * @param context         안드로이드 컨텍스트
     * @param destinationName 목적지 이름 (음성 인식된 결과물 등)
     * @param destLat         목적지 위도 (WGS84)
     * @param destLng         목적지 경도 (WGS84)
     */
    public static void startNaverNavigation(Context context, String destinationName, double destLat, double destLng) {
        String appPackageName = context.getPackageName();
        
        // 1. 목적지 이름 한글/공백 깨짐 방지를 위한 URL 인코딩 수행
        String encodedDName = Uri.encode(destinationName);
        
        // 2. 실시간 내비게이션 주행 전용 스키마 문자열 생성 (navigation 사용)
        String schemeUrl = "nmap://navigation?dlat=" + destLat + "&dlng=" + destLng + "&dname=" + encodedDName + "&appname=" + appPackageName;
        
        try {
            // 3. Intent.parseUri를 사용하여 인텐트 생성
            Intent intent = Intent.parseUri(schemeUrl, Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null); // 외부 앱 호출을 위해 컴포넌트 초기화
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 비Activity 컨텍스트 지원
            
            // 4. 인텐트 실행 (네이버 지도 내비게이션 화면 켜짐)
            Log.d("MapIntegration", "네이버 내비게이션 Intent 호출 시작 - Scheme URL: " + schemeUrl + ", Destination: " + destinationName + ", Lat: " + destLat + ", Lng: " + destLng);
            context.startActivity(intent);
            Log.d("MapIntegration", "네이버 내비게이션 실행 성공: " + destinationName);
            
        } catch (Exception e) {
            Log.e("MapIntegration", "인텐트 파싱 또는 앱 실행 실패", e);
        }
    }

    /**
     * 티맵 오토 앱을 실행하여 목적지로 즉시 실시간 내비게이션 주행을 시작합니다.
     * 출발지는 자동으로 사용자의 '현재 위치'로 지정됩니다.
     *
     * @param context         안드로이드 컨텍스트
     * @param destinationName 목적지 이름 (음성 인식된 결과물 등)
     * @param destLat         목적지 위도 (WGS84)
     * @param destLng         목적지 경도 (WGS84)
     */
    public static void startTmapNavigation(Context context, String destinationName, double destLat, double destLng) {
        String encodedDName = Uri.encode(destinationName);
        String tmapPkg = getInstalledTmapAutoPackage(context);
        
        Log.d("MapIntegration", "티맵 오토 내비게이션 실행 시도. 감지된 패키지: " + tmapPkg);
        
        if (tmapPkg == null) {
            tmapPkg = "com.tmap.auto.byd";
        }

        // 1. URI 스키마 준비 (일반 스키마 및 geo 스키마 모두 대응)
        String tmapUri = "tmap://route?goalx=" + destLng + "&goaly=" + destLat + "&name=" + encodedDName;
        String geoUri = "geo:" + destLat + "," + destLng + "?q=" + encodedDName;

        // 2. 브로드캐스트 송신기 및 JSON 데이터 정의
        final String fTmapPkg = tmapPkg;
        final String fDestinationName = destinationName;
        final double fDestLat = destLat;
        final double fDestLng = destLng;
        
        final Runnable sendRouteBroadcast = new Runnable() {
            @Override
            public void run() {
                try {
                    // TmapAutoReceiver는 'location' 키의 JSON 문자열을 DestinationInfo 객체로 역직렬화합니다.
                    String locationJson = "{"
                        + "\"isEvStation\":false,"
                        + "\"latitude\":" + fDestLat + ","
                        + "\"longitude\":" + fDestLng + ","
                        + "\"name\":\"" + fDestinationName.replace("\"", "\\\"") + "\","
                        + "\"pkey\":\"\","
                        + "\"poiid\":\"\""
                        + "}";

                    Intent intentBroadcast = new Intent("com.tmap.auto.api.ROUTE");
                    intentBroadcast.setPackage(fTmapPkg);
                    intentBroadcast.putExtra("location", locationJson);
                    
                    // 기존 개별 파라미터들도 호환성 유지를 위해 유지
                    intentBroadcast.putExtra("rGoName", fDestinationName);
                    intentBroadcast.putExtra("rGoX", String.valueOf(fDestLng));
                    intentBroadcast.putExtra("rGoY", String.valueOf(fDestLat));
                    intentBroadcast.putExtra("goalx", String.valueOf(fDestLng));
                    intentBroadcast.putExtra("goaly", String.valueOf(fDestLat));
                    intentBroadcast.putExtra("goalname", fDestinationName);
                    intentBroadcast.putExtra("name", fDestinationName);
                    
                    context.sendBroadcast(intentBroadcast);
                    Log.d("MapIntegration", "com.tmap.auto.api.ROUTE Broadcast 송신 완료 (location JSON 포함)");
                } catch (Exception e) {
                    Log.e("MapIntegration", "Broadcast 송신 실패", e);
                }
            }
        };

        boolean launched = false;

        // 3. SplashActivity(런처)를 ACTION_LAUNCHER_GOTO 액션으로 실행
        // 스플래시 화면을 거쳐야 인증 및 맵 엔진이 정상 로드된 후 목적지 화면이 활성화될 수 있습니다.
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(tmapPkg);
            if (intent != null) {
                intent.setAction("com.skt.tmap.auto.ACTION_LAUNCHER_GOTO");
                intent.setData(Uri.parse(tmapUri));
                
                // type이 null이면 SplashActivity 및 TmapAutoNaviActivity 내부에서 isEmpty() 호출 시 NPE(크래시)가 발생하므로 빈 값을 강제 주입합니다.
                intent.putExtra("type", "");
                
                // 다양한 형태의 목적지 extras 주입
                intent.putExtra("rGoName", destinationName);
                intent.putExtra("rGoX", String.valueOf(destLng));
                intent.putExtra("rGoY", String.valueOf(destLat));
                intent.putExtra("goalx", String.valueOf(destLng));
                intent.putExtra("goaly", String.valueOf(destLat));
                intent.putExtra("goalname", destinationName);
                intent.putExtra("name", destinationName);
                
                context.startActivity(intent);
                Log.d("MapIntegration", "SplashActivity 런처 실행 성공 (ACTION_LAUNCHER_GOTO + Package: " + tmapPkg + ")");
                launched = true;
            }
        } catch (Exception e) {
            Log.e("MapIntegration", "SplashActivity 런처 실행 실패", e);
        }

        // 4. ACTION_GOTO 액션 + 패키지명 (TmapAutoNaviActivity 직접 호출)
        try {
            Intent intentGoto = new Intent("com.skt.tmap.auto.ACTION_GOTO");
            intentGoto.setComponent(new ComponentName(tmapPkg, "com.skt.tmap.auto.activity.TmapAutoNaviActivity"));
            intentGoto.setData(Uri.parse(tmapUri));
            intentGoto.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // type NPE 방지
            intentGoto.putExtra("type", "");
            
            intentGoto.putExtra("rGoName", destinationName);
            intentGoto.putExtra("rGoX", String.valueOf(destLng));
            intentGoto.putExtra("rGoY", String.valueOf(destLat));
            intentGoto.putExtra("goalx", String.valueOf(destLng));
            intentGoto.putExtra("goaly", String.valueOf(destLat));
            intentGoto.putExtra("goalname", destinationName);
            intentGoto.putExtra("name", destinationName);
            
            context.startActivity(intentGoto);
            Log.d("MapIntegration", "TmapAutoNaviActivity 실행 성공 (ACTION_GOTO + Package: " + tmapPkg + ")");
            launched = true;
        } catch (Exception e) {
            Log.e("MapIntegration", "TmapAutoNaviActivity 실행 실패", e);
        }

        // 4-1. 액티비티가 포그라운드로 올라오고 이벤트 버스를 정상 등록할 시간을 확보하기 위해 1초 후 브로드캐스트 재송신
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(sendRouteBroadcast, 1000);
        } catch (Exception e) {
            Log.e("MapIntegration", "지연 브로드캐스트 예약 실패", e);
        }

        // 5. 일반 앱 런처 실행 (위의 특정 액션들이 전부 거부될 경우의 폴백)
        if (!launched) {
            try {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(tmapPkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    Log.d("MapIntegration", "일반 앱 런처 실행 성공: " + tmapPkg);
                }
            } catch (Exception e) {
                Log.e("MapIntegration", "일반 앱 런처 실행 실패", e);
            }
        }
    }

    private static String getInstalledTmapAutoPackage(Context context) {
        String[] tmapPackages = {
            "com.tmap.auto.byd",
            "com.skt.tmap.auto",
            "com.skt.tmap.ku"
        };
        for (String pkg : tmapPackages) {
            try {
                context.getPackageManager().getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean tryStartIntent(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w("MapIntegration", "Intent 실행 실패: " + intent.getAction() + " (URI: " + intent.getDataString() + ")", e);
            return false;
        }
    }
}
