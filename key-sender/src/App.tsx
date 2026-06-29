/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import ggwave_factory from 'ggwave';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Volume2, 
  VolumeX, 
  Play, 
  Square, 
  Radio, 
  KeyRound, 
  ShieldCheck, 
  Copy, 
  Check, 
  RefreshCw,
  HelpCircle,
  FileJson,
  Volume1,
  Waves,
  Zap
} from 'lucide-react';
import { LanguageProvider, useLanguage } from './i18n';

interface GGWaveContext {
  getDefaultParameters: () => any;
  init: (params: any) => number;
  free: (instance: number) => void;
  encode: (instance: number, payload: string, protocolId: number, volume: number) => Uint8Array;
  TxProtocolId?: Record<string, number>;
  ProtocolId?: Record<string, number>;
  SampleFormat?: Record<string, number>;
}

export default function App() {
  return (
    <LanguageProvider>
      <MainApp />
    </LanguageProvider>
  );
}

function MainApp() {
  const { t, language, setLanguage } = useLanguage();
  const [geminiKey, setGeminiKey] = useState<string>('');
  const [geminiModel, setGeminiModel] = useState<string>('gemini-3.1-flash-live-preview');
  const [naverId, setNaverId] = useState<string>('');
  const [naverSecret, setNaverSecret] = useState<string>('');
  const [kakaoKey, setKakaoKey] = useState<string>('');

  // Interface state
  const [volume, setVolume] = useState<number>(60);
  const [protocol, setProtocol] = useState<string>('audible_fast');
  const [isTransmitting, setIsTransmitting] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);
  const [explainOpen, setExplainOpen] = useState<boolean>(false);

  // Wasm Module references
  const [ggwave, setGgwave] = useState<GGWaveContext | null>(null);
  const [ggwaveInstance, setGgwaveInstance] = useState<number | null>(null);
  const [ggwaveParams, setGgwaveParams] = useState<any>(null);
  const [loadingState, setLoadingState] = useState<'loading' | 'success' | 'error'>('loading');

  // Audio elements references
  const audioCtxRef = useRef<AudioContext | null>(null);
  const audioSourceRef = useRef<AudioBufferSourceNode | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animationRef = useRef<number | null>(null);

  // Split-playback state variables
  const [currentPlayIndex, setCurrentPlayIndex] = useState<number | null>(null);
  const [totalPlayItems, setTotalPlayItems] = useState<number | null>(null);
  const [currentPayloadText, setCurrentPayloadText] = useState<string>('');
  const [isSoundActive, setIsSoundActive] = useState<boolean>(false);

  const isSoundActiveRef = useRef<boolean>(false);
  const cancelTransmissionRef = useRef<boolean>(false);

  // Initialize GGWave WASM
  useEffect(() => {
    let activeInstance: number | null = null;
    let activeGgwave: GGWaveContext | null = null;

    const initModule = async () => {
      try {
        setLoadingState('loading');
        // Handle commonJS or ESM default exports
        const resolvedFactory = (ggwave_factory as any).default || ggwave_factory;
        const gg = (await resolvedFactory()) as GGWaveContext;

        const parameters = gg.getDefaultParameters();
        // Set sample format out to Float32 so we can play floats via Web Audio API
        if (gg.SampleFormat) {
          parameters.sampleFormatOut = gg.SampleFormat.GGWAVE_SAMPLE_FORMAT_F32;
        }

        const instance = gg.init(parameters);
        
        activeInstance = instance;
        activeGgwave = gg;

        setGgwave(gg);
        setGgwaveParams(parameters);
        setGgwaveInstance(instance);
        setLoadingState('success');
      } catch (err) {
        console.error('Failed to load GGWave WASM:', err);
        setLoadingState('error');
      }
    };

    initModule();

    return () => {
      if (activeGgwave && activeInstance !== null) {
        try {
          activeGgwave.free(activeInstance);
        } catch (e) {
          console.error('Error freeing GGWave instance:', e);
        }
      }
      if (audioSourceRef.current) {
        try {
          audioSourceRef.current.stop();
        } catch (e) {}
      }
    };
  }, []);

  // Compute live compact JSON string representation without whitespace and using short keys
  const compactPayloadObj: Record<string, string> = {};
  if (geminiKey) compactPayloadObj.g = geminiKey;
  if (geminiModel) compactPayloadObj.m = geminiModel;
  if (naverId) compactPayloadObj.i = naverId;
  if (naverSecret) compactPayloadObj.s = naverSecret;
  if (kakaoKey) compactPayloadObj.k = kakaoKey;

  const jsonPayload = JSON.stringify(compactPayloadObj);



  // Helper to discover protocol ID securely from our defensive mapping
  const resolveProtocolId = (gg: GGWaveContext, protoName: string): number => {
    const protocolEnum = gg.ProtocolId || gg.TxProtocolId || (gg as any).Protocol || {};
    
    let key = 'GGWAVE_PROTOCOL_AUDIBLE_FAST';
    if (protoName === 'audible_normal') key = 'GGWAVE_PROTOCOL_AUDIBLE_NORMAL';
    if (protoName === 'audible_fastest') key = 'GGWAVE_PROTOCOL_AUDIBLE_FASTEST';
    if (protoName === 'ultrasound_fast') key = 'GGWAVE_PROTOCOL_ULTRASOUND_FAST';
    if (protoName === 'ultrasound_normal') key = 'GGWAVE_PROTOCOL_ULTRASOUND_NORMAL';

    // fallback mapping if keys are not spelled exact
    const rawFallbacks: Record<string, number> = {
      audible_normal: 0,
      audible_fast: 1,
      audible_fastest: 2,
      ultrasound_normal: 3,
      ultrasound_fast: 4,
      ultrasound_fastest: 5,
    };

    if (protocolEnum[key] !== undefined) {
      return protocolEnum[key];
    }

    // Try alternate format
    const altKey = key.replace('GGWAVE_PROTOCOL_', 'GGWAVE_TX_PROTOCOL_');
    if (protocolEnum[altKey] !== undefined) {
      return protocolEnum[altKey];
    }

    return rawFallbacks[protoName] ?? 1;
  };

  // Handle Playback transmit
  const handleTransmit = async () => {
    if (isTransmitting) {
      // Toggle off / Stop
      stopTransmission();
      return;
    }

    if (!ggwave || ggwaveInstance === null || !ggwaveParams) {
      alert(t('alertEngineNotLoaded'));
      return;
    }

    // Build sub-transmission targets based on 140b size limit
    const compactPayloadObj: Record<string, string> = {};
    if (geminiKey) compactPayloadObj.g = geminiKey;
    if (geminiModel) compactPayloadObj.m = geminiModel;
    if (naverId) compactPayloadObj.i = naverId;
    if (naverSecret) compactPayloadObj.s = naverSecret;
    if (kakaoKey) compactPayloadObj.k = kakaoKey;

    const currentJsonPayload = JSON.stringify(compactPayloadObj);
    const bytesCount = new TextEncoder().encode(currentJsonPayload).length;

    let queue: string[] = [];
    if (bytesCount <= 140) {
      queue = [currentJsonPayload];
    } else {
      // Exceeds 140 bytes! Split into separate messages with 1s delays sequentially
      if (geminiKey) queue.push(JSON.stringify({ g: geminiKey }));
      if (geminiModel) queue.push(JSON.stringify({ m: geminiModel }));
      if (naverId) queue.push(JSON.stringify({ i: naverId }));
      if (naverSecret) queue.push(JSON.stringify({ s: naverSecret }));
      if (kakaoKey) queue.push(JSON.stringify({ k: kakaoKey }));
    }

    if (queue.length === 0 || Object.keys(compactPayloadObj).length === 0) {
      alert(t('alertEmptyFields'));
      return;
    }

    try {
      setIsTransmitting(true);
      cancelTransmissionRef.current = false;
      setTotalPlayItems(queue.length);

      // Create Web Audio Context once
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      const context = new AudioCtxClass();
      audioCtxRef.current = context;

      const protoId = resolveProtocolId(ggwave, protocol);
      const sampleRate = ggwaveParams.sampleRateOut || 48000;

      for (let i = 0; i < queue.length; i++) {
        if (cancelTransmissionRef.current) break;

        const currentMsg = queue[i];
        setCurrentPlayIndex(i + 1);
        setCurrentPayloadText(currentMsg);

        // Turn on acoustic play visualization states
        isSoundActiveRef.current = true;
        setIsSoundActive(true);

        const waveformBytes = ggwave.encode(ggwaveInstance, currentMsg, protoId, volume);
        if (!waveformBytes || waveformBytes.byteLength === 0) {
          throw new Error(t('alertEncodeEmpty'));
        }

        const sampleCount = waveformBytes.byteLength / 4;
        const waveformFloats = new Float32Array(
          waveformBytes.buffer,
          waveformBytes.byteOffset,
          sampleCount
        );

        const buffer = context.createBuffer(1, sampleCount, sampleRate);
        buffer.getChannelData(0).set(waveformFloats);

        const source = context.createBufferSource();
        source.buffer = buffer;
        source.connect(context.destination);
        audioSourceRef.current = source;

        await new Promise<void>((resolvePlay, rejectPlay) => {
          source.onended = () => {
            resolvePlay();
          };
          try {
            source.start();
          } catch (e) {
            rejectPlay(e);
          }
        });

        // Current payload sound ended
        isSoundActiveRef.current = false;
        setIsSoundActive(false);

        // Add 1-second delay between sequential signals
        if (i < queue.length - 1 && !cancelTransmissionRef.current) {
          setCurrentPayloadText(t('intervalText'));
          await new Promise<void>(resolveDelay => {
            const timer = setTimeout(() => {
              resolveDelay();
            }, 1000);

            // Allow short-circuiting delay immediately on cancel
            const checkCancel = setInterval(() => {
              if (cancelTransmissionRef.current) {
                clearTimeout(timer);
                clearInterval(checkCancel);
                resolveDelay();
              }
            }, 50);
          });
        }
      }

      // Final Cleanup on completion
      setIsTransmitting(false);
      isSoundActiveRef.current = false;
      setIsSoundActive(false);
      setCurrentPayloadText('');
      setCurrentPlayIndex(null);
      setTotalPlayItems(null);

      if (audioCtxRef.current) {
        audioCtxRef.current.close().catch(() => {});
        audioCtxRef.current = null;
      }
    } catch (err: any) {
      console.error('Transmission error:', err);
      alert(`${t('alertPlayError')}${err?.message || err}`);
      stopTransmission();
    }
  };

  const stopTransmission = () => {
    cancelTransmissionRef.current = true;
    isSoundActiveRef.current = false;
    setIsSoundActive(false);

    if (audioSourceRef.current) {
      try {
        audioSourceRef.current.stop();
      } catch (e) {}
      audioSourceRef.current = null;
    }

    if (audioCtxRef.current) {
      try {
        audioCtxRef.current.close();
      } catch (e) {}
      audioCtxRef.current = null;
    }

    setIsTransmitting(false);
    setCurrentPayloadText('');
    setCurrentPlayIndex(null);
    setTotalPlayItems(null);
  };

  // Copy JSON payload output to clipboard helper
  const copyToClipboard = () => {
    navigator.clipboard.writeText(jsonPayload);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Sound ripple visualizer canvas animation
  useEffect(() => {
    if (!isTransmitting) {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
        animationRef.current = null;
      }
      return;
    }

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let frame = 0;
    
    // Set matching dimensions
    canvas.width = canvas.parentElement?.clientWidth || 350;
    canvas.height = 120;

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2;
      frame++;

      // Background graph grid
      ctx.strokeStyle = 'rgba(241, 245, 249, 0.05)';
      ctx.lineWidth = 1;
      for (let i = 0; i < canvas.width; i += 40) {
        ctx.beginPath();
        ctx.moveTo(i, 0);
        ctx.lineTo(i, canvas.height);
        ctx.stroke();
      }

      // Draw concentric sound ripples
      const maxRadius = Math.max(canvas.width, canvas.height) * 0.4;
      const numRings = 4;
      const isSoundPlaying = isSoundActiveRef.current;
      for (let i = 0; i < numRings; i++) {
        const offset = (frame * 1.5 + (i * maxRadius) / numRings) % maxRadius;
        const opacity = 1 - offset / maxRadius;
        ctx.beginPath();
        ctx.arc(centerX, centerY, offset, 0, Math.PI * 2);
        const ringOpacityScale = isSoundPlaying ? 1 : 0.25;
        ctx.strokeStyle = `rgba(14, 165, 233, ${opacity * 0.35 * ringOpacityScale})`; // sky-500
        ctx.lineWidth = 1.5;
        ctx.stroke();
      }

      // Draw active modulated waves
      ctx.beginPath();
      // Gradient effect
      const gradient = ctx.createLinearGradient(0, 0, canvas.width, 0);
      gradient.addColorStop(0, 'rgba(56, 189, 248, 0.2)'); // sky-400
      gradient.addColorStop(0.5, 'rgba(14, 165, 233, 0.9)'); // sky-500
      gradient.addColorStop(1, 'rgba(56, 189, 248, 0.2)');
      
      ctx.strokeStyle = gradient;
      ctx.lineWidth = 3;
      
      for (let x = 0; x < canvas.width; x += 3) {
        const distFromCenter = Math.abs(x - centerX);
        const envelope = Math.max(0, 1 - distFromCenter / (canvas.width * 0.45));
        
        // Complex carrier wave drawing
        const carrier1 = Math.sin(x * 0.07 - frame * 0.12);
        const carrier2 = Math.sin(x * 0.18 + frame * 0.05);
        const modulator = Math.cos(x * 0.015 - frame * 0.02);
        
        const heightMultiplier = isSoundPlaying ? 35 : 3.5;
        const sineWave = (carrier1 * 0.6 + carrier2 * 0.4) * modulator * heightMultiplier * envelope;
        
        if (x === 0) {
          ctx.moveTo(x, centerY + sineWave);
        } else {
          ctx.lineTo(x, centerY + sineWave);
        }
      }
      ctx.stroke();

      // Simple baseline axis
      ctx.beginPath();
      ctx.strokeStyle = 'rgba(226, 232, 240, 0.255)';
      ctx.moveTo(0, centerY);
      ctx.lineTo(canvas.width, centerY);
      ctx.stroke();

      // Pulse circle
      ctx.beginPath();
      ctx.arc(centerX, centerY, 6, 0, Math.PI * 2);
      ctx.fillStyle = '#0ea5e9';
      ctx.fill();

      animationRef.current = requestAnimationFrame(draw);
    };

    draw();

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [isTransmitting]);

  return (
    <div className="min-h-screen bg-slate-950 font-sans text-slate-100 selection:bg-sky-500/30 flex flex-col items-center justify-center p-4 py-8 md:p-8">
      
      <div className="glass-panel w-full max-w-md md:max-w-lg p-6 md:p-8 flex flex-col gap-6 relative overflow-hidden transition-all duration-300">
        
        {/* Top Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-sky-500/10 border border-sky-500/20 p-2.5 shadow-md">
              <Radio className={`h-5 w-5 text-sky-400 ${isTransmitting ? 'animate-pulse' : ''}`} />
            </div>
            <div>
              <h1 className="font-display text-lg font-bold tracking-tight uppercase text-white">Byd SubAI Key Sender</h1>
              <p className="text-[10px] text-slate-400 font-mono tracking-wider">GGWAVE TRANSMITTER</p>
            </div>
          </div>
          
          <div className="flex items-center gap-2">
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value as any)}
              className="rounded-lg px-2.5 py-1.5 bg-white/5 border border-white/10 hover:bg-white/10 text-slate-300 text-xs focus:outline-none focus:ring-1 focus:ring-sky-500/50 cursor-pointer transition-all duration-200"
            >
              <option value="ko" className="bg-slate-900 text-slate-300">한국어</option>
              <option value="en" className="bg-slate-900 text-slate-300">English</option>
              <option value="ja" className="bg-slate-900 text-slate-300">日本語</option>
            </select>

            <button 
              onClick={() => setExplainOpen(!explainOpen)}
              className="rounded-lg p-2 bg-white/5 border border-white/5 hover:bg-white/10 transition-colors cursor-pointer"
              title={t('explain')}
            >
              <HelpCircle className="h-4 w-4 text-slate-300" />
            </button>
          </div>
        </div>

        {/* Info Explainer block */}
        <AnimatePresence>
          {explainOpen && (
            <motion.div 
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="overflow-hidden bg-slate-950/40 border-l-2 border-sky-500/60 rounded-r-xl"
            >
              <div className="p-4 text-xs text-slate-300 space-y-2 leading-relaxed">
                <p className="font-semibold text-sky-400">{t('explainTitle')}</p>
                <p>{t('explainDesc1')}</p>
                <p>{t('explainDesc2')}</p>
                <p className="text-[10px] text-sky-400/70 font-mono">
                  {t('explainSecurity')}
                </p>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Main Workspace */}
        <div className="flex flex-col gap-6">
          
          {/* Module loading status banner */}
          {loadingState === 'loading' && (
            <div className="flex items-center gap-3 rounded-xl bg-sky-500/10 border border-sky-500/20 p-3.5 text-xs text-sky-300 font-medium animate-pulse">
              <RefreshCw className="h-4 w-4 animate-spin text-sky-400" />
              <span>{t('loadingEngine')}</span>
            </div>
          )}

          {loadingState === 'error' && (
            <div className="rounded-xl bg-rose-500/10 p-3.5 text-xs text-rose-300 border border-rose-500/20">
              <p className="font-semibold">{t('errorEngineTitle')}</p>
              <p className="text-slate-400 mt-1">{t('errorEngineDesc')}</p>
            </div>
          )}

          {/* Form Credentials Section - 3 Edittexts */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{t('inputSectionTitle')}</h2>
            </div>

            {/* Input 1: Gemini API Key */}
            <div className="space-y-1.5" id="field-gemini">
              <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-1 flex justify-between">
                <span>{t('geminiKeyLabel')}</span>
                <span className="text-[9px] text-slate-500 font-normal lowercase font-mono">gemini_api_key</span>
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <KeyRound className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  value={geminiKey}
                  onChange={(e) => setGeminiKey(e.target.value)}
                  placeholder={t('placeholderGeminiKey')}
                  className="input-field block w-full py-2.5 pl-9 pr-3 text-sm font-mono placeholder:text-slate-600"
                />
              </div>
            </div>

            {/* Input 1.5: Gemini Model */}
            <div className="space-y-1.5" id="field-gemini-model">
              <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-1 flex justify-between">
                <span>{t('geminiModelLabel')}</span>
                <span className="text-[9px] text-slate-500 font-normal lowercase font-mono">gemini_model</span>
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <Zap className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  value={geminiModel}
                  onChange={(e) => setGeminiModel(e.target.value)}
                  placeholder={t('placeholderGeminiModel')}
                  className="input-field block w-full py-2.5 pl-9 pr-3 text-sm font-mono placeholder:text-slate-600"
                />
              </div>
            </div>

            {/* Input 2: Naver Client ID */}
            <div className="space-y-1.5" id="field-naver-id">
              <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-1 flex justify-between">
                <span>{t('naverIdLabel')}</span>
                <span className="text-[9px] text-slate-500 font-normal lowercase font-mono">naver_client_id</span>
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <FileJson className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  value={naverId}
                  onChange={(e) => setNaverId(e.target.value)}
                  placeholder={t('placeholderNaverId')}
                  className="input-field block w-full py-2.5 pl-9 pr-3 text-sm font-mono placeholder:text-slate-600"
                />
              </div>
            </div>

            {/* Input 3: Naver Client Secret */}
            <div className="space-y-1.5" id="field-naver-secret">
              <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-1 flex justify-between">
                <span>{t('naverSecretLabel')}</span>
                <span className="text-[9px] text-slate-500 font-normal lowercase font-mono">naver_client_secret</span>
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <ShieldCheck className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  value={naverSecret}
                  onChange={(e) => setNaverSecret(e.target.value)}
                  placeholder={t('placeholderNaverSecret')}
                  className="input-field block w-full py-2.5 pl-9 pr-3 text-sm font-mono placeholder:text-slate-600"
                />
              </div>
            </div>

            {/* Input 4: Kakao API Key */}
            <div className="space-y-1.5" id="field-kakao-key">
              <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-1 flex justify-between">
                <span>{t('kakaoKeyLabel')}</span>
                <span className="text-[9px] text-slate-500 font-normal lowercase font-mono">kakao_api_key</span>
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <ShieldCheck className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  value={kakaoKey}
                  onChange={(e) => setKakaoKey(e.target.value)}
                  placeholder={t('placeholderKakaoKey')}
                  className="input-field block w-full py-2.5 pl-9 pr-3 text-sm font-mono placeholder:text-slate-600"
                />
              </div>
            </div>
          </div>

          {/* Transmit Visual Signal Space */}
          <div className="relative rounded-2xl bg-black/40 overflow-hidden flex flex-col items-center justify-center min-h-[140px] px-4 py-2 border border-white/5 shadow-inner">
            {isTransmitting ? (
              <div className="w-full relative h-[120px] flex items-center justify-center">
                <canvas ref={canvasRef} className="absolute inset-0 w-full h-full" />
                <div className="absolute bottom-2 right-2 left-2 flex items-center justify-between text-[10px] font-mono text-slate-300 bg-slate-950/85 px-3 py-1.5 rounded-lg border border-white/5 backdrop-blur-md">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className={`inline-block w-2 h-2 rounded-full ${isSoundActive ? 'bg-sky-400 animate-pulse' : 'bg-slate-600'}`}></span>
                    <span className="truncate max-w-[200px]" title={currentPayloadText || ""}>
                      {currentPayloadText || t('statusWaiting')}
                    </span>
                  </div>
                  {totalPlayItems !== null && currentPlayIndex !== null && (
                    <span className="text-sky-400 font-bold shrink-0">
                      {currentPlayIndex} / {totalPlayItems} ({isSoundActive ? t('statusTransmitting') : t('statusInterval')})
                    </span>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-2 py-4">
                <div className="rounded-full bg-white/5 p-4 border border-white/10">
                  <Waves className="h-5 w-5 text-slate-400 stroke-[1.5]" />
                </div>
                <p className="text-xs text-slate-300 text-center font-medium">{t('waitingSignal')}</p>
                <p className="text-[10px] text-slate-500 text-center max-w-[220px]">
                  {t('waitingSignalDesc')}
                </p>
              </div>
            )}
          </div>

          {/* Select protocol segment & Volume control */}
          <div className="bg-white/5 border border-white/10 rounded-2xl p-4 space-y-4 shadow-sm">
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-slate-300">{t('protocolTitle')}</span>
                <span className="text-[10px] bg-sky-500/10 text-sky-400 border border-sky-500/20 px-1.5 py-0.5 rounded font-bold font-mono">FSK</span>
              </div>
              <div className="grid grid-cols-3 gap-1.5 bg-black/40 p-1.5 rounded-xl">
                <button
                  type="button"
                  onClick={() => setProtocol('audible_fast')}
                  className={`py-1.5 text-xs font-semibold rounded-lg transition-all flex flex-col items-center gap-1 cursor-pointer ${
                     protocol === 'audible_fast'
                      ? 'bg-sky-500 text-white shadow-md shadow-sky-500/15'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                  }`}
                >
                  <Volume2 className="h-3.5 w-3.5" />
                  <span>{t('protocolAudibleFast')}</span>
                </button>
                
                <button
                  type="button"
                  onClick={() => setProtocol('audible_fastest')}
                  className={`py-1.5 text-xs font-semibold rounded-lg transition-all flex flex-col items-center gap-1 cursor-pointer ${
                     protocol === 'audible_fastest'
                      ? 'bg-sky-500 text-white shadow-md shadow-sky-500/15'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                  }`}
                >
                  <Zap className="h-3.5 w-3.5" />
                  <span>{t('protocolAudibleFastest')}</span>
                </button>

                <button
                  type="button"
                  onClick={() => setProtocol('ultrasound_fast')}
                  className={`py-1.5 text-xs font-semibold rounded-lg transition-all flex flex-col items-center gap-1 cursor-pointer ${
                     protocol === 'ultrasound_fast'
                      ? 'bg-sky-500 text-white shadow-md shadow-sky-500/15'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                  }`}
                >
                  <VolumeX className="h-3.5 w-3.5" />
                  <span>{t('protocolUltrasoundFast')}</span>
                </button>
              </div>
              <p className="text-[10px] text-slate-500 leading-normal">
                {protocol === 'audible_fast' && t('descAudibleFast')}
                {protocol === 'audible_fastest' && t('descAudibleFastest')}
                {protocol === 'ultrasound_fast' && t('descUltrasoundFast')}
              </p>
            </div>

            {/* Volume bar */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs text-slate-300 font-bold">
                <span>{t('volumeTitle')}</span>
                <span>{volume}%</span>
              </div>
              <div className="flex items-center gap-3">
                {volume === 0 ? (
                  <VolumeX className="h-4 w-4 text-slate-500" />
                ) : volume < 50 ? (
                  <Volume1 className="h-4 w-4 text-slate-400" />
                ) : (
                  <Volume2 className="h-4 w-4 text-sky-400" />
                )}
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={volume}
                  onChange={(e) => setVolume(Number(e.target.value))}
                  className="w-full accent-sky-500 h-1 rounded-lg bg-black/40 cursor-pointer"
                />
              </div>
            </div>
          </div>

          {/* JSON code preview block */}
          <div className="space-y-2.5">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-slate-300 flex items-center gap-1.5">
                <FileJson className="h-4 w-4 text-sky-400" />
                {t('previewTitle')}
                <span className={`text-[10px] font-mono font-medium px-2 py-0.5 rounded-full ${
                  new TextEncoder().encode(jsonPayload).length <= 140 
                    ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                    : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                }`}>
                  {new TextEncoder().encode(jsonPayload).length}B 
                  {new TextEncoder().encode(jsonPayload).length <= 140 ? t('singlePacket') : t('multiPacket')}
                </span>
              </span>
              <button
                onClick={copyToClipboard}
                className="text-xs flex items-center gap-1 text-slate-400 hover:text-sky-300 select-none py-1 px-2 hover:bg-white/5 rounded transition-colors"
              >
                {copied ? (
                  <>
                    <Check className="h-3.5 w-3.5 text-emerald-400" />
                    <span className="text-emerald-400 font-medium">{t('copied')}</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3.5 w-3.5" />
                    <span>{t('copy')}</span>
                  </>
                )}
              </button>
            </div>
            
            <pre className="font-mono text-[11px] overflow-x-auto bg-black/40 text-slate-300 p-4 rounded-xl border border-white/5 shadow-sm leading-relaxed max-h-[160px]">
              <code>{jsonPayload}</code>
            </pre>
          </div>

        </div>

        {/* Global Footer Controls */}
        <div className="border-t border-white/10 pt-5 text-center">
          
          {/* Primary Action Play Trigger */}
          <button
            onClick={handleTransmit}
            disabled={loadingState === 'loading'}
            className={`w-full py-4 px-6 rounded-2xl font-bold flex items-center justify-center gap-2.5 shadow-lg transition-all duration-300 transform select-none cursor-pointer active:scale-[0.98] ${
              isTransmitting 
                ? 'bg-rose-500 hover:bg-rose-600 text-white shadow-rose-500/20'
                : 'bg-sky-500 hover:bg-sky-400 text-white shadow-sky-500/20'
            } disabled:opacity-50`}
          >
            {isTransmitting ? (
              <>
                <Square className="h-5 w-5 fill-white" />
                <span>{t('btnStop')}</span>
              </>
            ) : (
              <>
                <Play className="h-5 w-5 fill-white" />
                <span>{t('btnPlay')}</span>
              </>
            )}
          </button>
          
          <p className="text-[10px] text-slate-500 mt-3 leading-normal">
            {t('footerWarning')}
          </p>
        </div>

      </div>

      {/* Sub-footer System Info Metadata matching Design HTML perfectly */}
      <div className="mt-6 text-slate-600 text-[10px] flex gap-4 uppercase tracking-[0.2em] justify-center items-center select-none">
        <span>Carrier: 18kHz</span>
        <span>•</span>
        <span>Protocol: GGWave-v1</span>
        <span>•</span>
        <span>Buffer: Ready</span>
      </div>

    </div>
  );
}
