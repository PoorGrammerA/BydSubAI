import React, { createContext, useContext, useState, useEffect } from 'react';

export type Language = 'en' | 'ko' | 'ja';

const translations: Record<Language, Record<string, string>> = {
  en: {
    explain: "Explanation",
    explainTitle: "🔊 What is GGWave acoustic transmission?",
    explainDesc1: "GGWave is a tiny wireless data transmission library that uses speakers and microphones of electronic devices to transmit data using sound frequencies (audio).",
    explainDesc2: "On this page, your entered Gemini API Key, Naver Client ID & Secret, and Kakao API Key are converted into a standard JSON structure, then encoded (FSK modulated) into audible sound waves (Audible Fast) or ultrasound (Ultrasound) and played back. The receiver device listens to this sound via microphone and decodes it back to data.",
    explainSecurity: "🔒 Security Note: All encoding and modulation are processed entirely offline within the browser (WASM). No data is ever sent to any remote server.",
    loadingEngine: "Loading acoustic modulation engine (WASM)...",
    errorEngineTitle: "⚠️ Failed to initialize acoustic engine",
    errorEngineDesc: "Please check your internet connection or refresh the page.",
    inputSectionTitle: "Enter Data to Transmit",
    geminiKeyLabel: "Gemini API Key",
    geminiLiveModelLabel: "Gemini Live Model",
    geminiNormalModelLabel: "Gemini Model",
    naverIdLabel: "NAVER Client ID",
    naverSecretLabel: "NAVER Secret",
    kakaoKeyLabel: "Kakao API Key",
    placeholderGeminiKey: "AIzaSy...",
    placeholderGeminiLiveModel: "gemini-3.1-flash-live-preview",
    placeholderGeminiNormalModel: "gemma-4-31b-it",
    placeholderNaverId: "Enter Client ID...",
    placeholderNaverSecret: "Enter Client Secret...",
    placeholderKakaoKey: "Enter Kakao API Key...",
    waitingSignal: "Waiting for Transmission",
    waitingSignalDesc: "Fill in the fields and tap the Play button below to activate the audio spectrum visualization in this area.",
    protocolTitle: "Acoustic Modulation Protocol",
    protocolAudibleFast: "Audible Fast",
    protocolAudibleFastest: "Audible Fastest",
    protocolUltrasoundFast: "Ultrasound (Silent)",
    descAudibleFast: "• Audible Fast: High-speed transmission generating high-pitched tones like robotic beeps.",
    descAudibleFastest: "• Audible Fastest: Maximum speed mode generating very dense and strong digital machine tones.",
    descUltrasoundFast: "• Ultrasound: Quiet transmission using inaudible frequencies above 18kHz.",
    volumeTitle: "Data Audio Volume",
    previewTitle: "Manual Transmission Data Structure (JSON)",
    singlePacket: " (Single transmission)",
    multiPacket: " (Exceeds 140B - split transmission with 1s intervals)",
    copied: "Copied!",
    copy: "Copy",
    btnStop: "Stop Transmission",
    btnPlay: "Start Acoustic Transmission (Play)",
    footerWarning: "* Make sure your device\'s media volume is turned up and play near the receiver.",
    alertEngineNotLoaded: "GGWave module is not loaded or failed to initialize.",
    alertEmptyFields: "Transmission data is empty. Please fill in the input fields.",
    alertEncodeEmpty: "Acoustic encoding returned empty audio data.",
    alertPlayError: "Failed to generate or play acoustic signal: ",
    intervalText: "Waiting 1s...",
    statusTransmitting: "Transmitting",
    statusInterval: "Interval",
    statusWaiting: "Waiting..."
  },
  ko: {
    explain: "설명란",
    explainTitle: "🔊 GGWave 음파 송신이란 무엇인가요?",
    explainDesc1: "GGWave는 전자기기에 장착된 스피커와 마이크를 이용하여 데이터를 음향 주파수(소리)로 전송하는 초소형 무선 프로토콜 데이터 라이브러리입니다.",
    explainDesc2: "이 페이지에서는 입력하신 Gemini API Key, Naver Client ID & Secret 및 Kakao API Key를 규격화된 JSON 객체 구조로 변환한 다음, 사람이 들을 수 있는 청각 음파 대역(Audible Fast) 또는 초음파 대역(Ultrasound)으로 부호화(FSK 변조)하여 소리로 송출(TX)합니다. 수신 장치는 마이크를 통해 이 소리를 듣고 다시 데이터로 복원하게 됩니다.",
    explainSecurity: "🔒 보안 안내: 모든 암호화 및 부호화 작업은 전적으로 브라우저 내부(WASM)에서 오프라인으로만 처리되며 서비스 서버로 데이터가 절대 전송되지 않습니다.",
    loadingEngine: "음파 변환 엔진(WASM)을 탑재하고 있습니다...",
    errorEngineTitle: "⚠️ 소리 변환 장치 준비 실패",
    errorEngineDesc: "인터넷 연결을 확인하거나 페이지를 새로고침 해주세요.",
    inputSectionTitle: "전송 데이터 내용 입력",
    geminiKeyLabel: "Gemini API Key",
    geminiLiveModelLabel: "Gemini Live 모델",
    geminiNormalModelLabel: "Gemini 모델",
    naverIdLabel: "NAVER Client ID",
    naverSecretLabel: "NAVER Secret",
    kakaoKeyLabel: "Kakao API Key",
    placeholderGeminiKey: "AIzaSy...",
    placeholderGeminiLiveModel: "gemini-3.1-flash-live-preview",
    placeholderGeminiNormalModel: "gemma-4-31b-it",
    placeholderNaverId: "ID 또는 클라이언트 키 입력",
    placeholderNaverSecret: "클라이언트 시크릿 토큰 입력",
    placeholderKakaoKey: "카카오 API 키 입력",
    waitingSignal: "송출 대기 중",
    waitingSignalDesc: "데이터를 작성하고 아래 재생 버튼을 탭하면 이 영역에 음화 스펙트럼이 활성화됩니다.",
    protocolTitle: "음파 변조 통신 프로토콜",
    protocolAudibleFast: "빠른 소리",
    protocolAudibleFastest: "광속 소리",
    protocolUltrasoundFast: "초음파 (무음)",
    descAudibleFast: "• Audible Fast: 고속 통전 방식으로, 로봇 비프음 같은 고음이 발생합니다.",
    descAudibleFastest: "• Audible Fastest: 최고 속도 모드로, 매우 촘촘하고 강한 디지털 기계음이 나옵니다.",
    descUltrasoundFast: "• Ultrasound: 사람이 들을 수 없는 18kHz 이상의 주파수로 정숙성이 유지됩니다.",
    volumeTitle: "데이터 음향 볼륨",
    previewTitle: "수동 전송 데이터 구조 (JSON)",
    singlePacket: " (단일 송출 가능)",
    multiPacket: " (140B 초과 - 1초 간격 분할 송출)",
    copied: "복사됨!",
    copy: "복사하기",
    btnStop: "데이터 전송 중지하기 (Stop)",
    btnPlay: "데이터 소리 송출 시작 (Play)",
    footerWarning: "* 기기의 가청 스피커 볼륨이 켜져 있는지 확인하고 수신기 근처에서 재생해 주세요.",
    alertEngineNotLoaded: "GGWave 모듈이 로드되지 않았거나 초기화에 실패했습니다.",
    alertEmptyFields: "송출할 데이터 내용이 비어 있습니다. 입력 필드를 작성해 주세요.",
    alertEncodeEmpty: "인코딩 결과 오디오 데이터가 빈 바이트로 수신되었습니다.",
    alertPlayError: "소리 신호 생성 또는 재생에 실패했습니다: ",
    intervalText: "1초 간격 대기 중...",
    statusTransmitting: "송출 중",
    statusInterval: "인터벌",
    statusWaiting: "대기 중..."
  },
  ja: {
    explain: "説明",
    explainTitle: "🔊 GGWave音波送信とは何ですか？",
    explainDesc1: "GGWaveは、電子機器に搭載されたスピーカーとマイクを利用してデータを音響周波数（音）で送信する超小型ワイヤレスプロトコルデータライブラリです。",
    explainDesc2: "このページでは、入力された Gemini API Key, Naver Client ID & Secret および Kakao API Key を規格化された JSON オブジェクト構造に変換し、人が聞き取れる可聴音波帯域（Audible Fast）または超音波帯域（Ultrasound）に符号化（FSK変調）して音として送出（TX）します。受信デバイスはマイクを介してこの音を聞き取り、再びデータに復元します。",
    explainSecurity: "🔒 セキュリティ案内: すべての暗号化および符号化作業は、完全にブラウザ内部(WASM)でオフラインでのみ処理され、サービスサーバーにデータが送信されることはありません。",
    loadingEngine: "音波変換エンジン(WASM)を搭載しています...",
    errorEngineTitle: "⚠️ 音響エンジンの初期化に失敗しました",
    errorEngineDesc: "インターネット接続を確認するか、ページを更新してください。",
    inputSectionTitle: "送信データ内容入力",
    geminiKeyLabel: "Gemini API Key",
    geminiLiveModelLabel: "Gemini Live モデル",
    geminiNormalModelLabel: "Gemini モデル",
    naverIdLabel: "NAVER Client ID",
    naverSecretLabel: "NAVER Secret",
    kakaoKeyLabel: "Kakao API Key",
    placeholderGeminiKey: "AIzaSy...",
    placeholderGeminiLiveModel: "gemini-3.1-flash-live-preview",
    placeholderGeminiNormalModel: "gemma-4-31b-it",
    placeholderNaverId: "IDまたはクライアントキーを入力",
    placeholderNaverSecret: "クライアントシークレットトークンを入力",
    placeholderKakaoKey: "Kakao APIキーを入力...",
    waitingSignal: "送信待機中",
    waitingSignalDesc: "データを入力して下の再生ボタンをタップすると、このエリアに音響スペクトルが有効になります。",
    protocolTitle: "音波変調通信プロトコル",
    protocolAudibleFast: "速い音",
    protocolAudibleFastest: "超高速音",
    protocolUltrasoundFast: "超音波 (無音)",
    descAudibleFast: "• Audible Fast: 高速伝送方式で、ロボットのビープ音のような高音が発生します。",
    descAudibleFastest: "• Audible Fastest: 最高速度モードで、非常に密で強力なデジタル機械音が出ます。",
    descUltrasoundFast: "• Ultrasound: 人には聞こえない18kHz以上の周波数で静粛性が維持されます。",
    volumeTitle: "データ音量",
    previewTitle: "手動送信データ構造 (JSON)",
    singlePacket: " (単一送信可能)",
    multiPacket: " (140B超過 - 1秒間隔で分割送信)",
    copied: "コピーされました！",
    copy: "コピー",
    btnStop: "データ送信停止 (Stop)",
    btnPlay: "データ音波送出開始 (Play)",
    footerWarning: "* デバイスの音量がオンになっていることを確認し、受信機の近くで再生してください。",
    alertEngineNotLoaded: "GGWaveモジュールがロードされていないか、初期化に失敗しました。",
    alertEmptyFields: "送信するデータが空です。入力フィールドに記入してください。",
    alertEncodeEmpty: "エンコード結果のオーディオデータが空のバイトで受信されました。",
    alertPlayError: "音響信号の生成または再生に失敗しました: ",
    intervalText: "1秒間隔待機中...",
    statusTransmitting: "送信中",
    statusInterval: "インターバル",
    statusWaiting: "待機中..."
  }
};

interface LanguageContextType {
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: string) => string;
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

const getBrowserLanguage = (): Language => {
  if (typeof navigator === 'undefined') return 'en';
  const browserLang = navigator.language || (navigator as any).userLanguage || 'en';
  if (browserLang.startsWith('ko')) return 'ko';
  if (browserLang.startsWith('ja')) return 'ja';
  return 'en';
};

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [language, setLanguageState] = useState<Language>('en');

  useEffect(() => {
    setLanguageState(getBrowserLanguage());
  }, []);

  const setLanguage = (lang: Language) => {
    setLanguageState(lang);
  };

  const t = (key: string): string => {
    return translations[language]?.[key] || translations['en']?.[key] || key;
  };

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error('useLanguage must be used within a LanguageProvider');
  }
  return context;
};
