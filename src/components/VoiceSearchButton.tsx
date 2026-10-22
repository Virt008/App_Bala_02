import React, { useState, useEffect, useRef } from 'react';
import { Mic, MicOff } from 'lucide-react';

interface VoiceSearchButtonProps {
  onTranscript: (text: string) => void;
  onNotice: (message: string) => void;
}

// Declaração de tipos para SpeechRecognition web
declare global {
  interface Window {
    SpeechRecognition: any;
    webkitSpeechRecognition: any;
  }
}

export const VoiceSearchButton: React.FC<VoiceSearchButtonProps> = ({
  onTranscript,
  onNotice,
}) => {
  const [isListening, setIsListening] = useState(false);
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch (_) {}
      }
    };
  }, []);

  const startListening = () => {
    const SpeechRecognition =
      window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      onNotice(
        'Reconhecimento de voz não suportado por este navegador. Por favor, utilize a busca por texto.'
      );
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognitionRef.current = recognition;

      recognition.lang = 'pt-BR';
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;

      recognition.onstart = () => {
        setIsListening(true);
      };

      recognition.onresult = (event: any) => {
        setIsListening(false);
        if (event.results && event.results[0] && event.results[0][0]) {
          const transcript = event.results[0][0].transcript;
          if (transcript) {
            onTranscript(transcript);
          }
        }
      };

      recognition.onerror = (event: any) => {
        setIsListening(false);
        const error = event.error;

        if (error === 'network') {
          onNotice(
            'Reconhecimento de voz sem conexão com a internet. Verifique sua rede ou instale o pacote de voz em Português no sistema.'
          );
        } else if (error === 'not-allowed' || error === 'service-not-allowed') {
          onNotice('Permissão de microfone negada. Autorize o acesso ao microfone no navegador/dispositivo para pesquisar por voz.');
        } else if (error === 'no-speech') {
          onNotice('Nenhuma voz identificada. Tente falar mais próximo ao microfone.');
        } else if (error === 'audio-capture') {
          onNotice('Falha na captura de áudio. Verifique se o microfone está conectado e funcionando.');
        } else if (error === 'language-not-supported') {
          onNotice('O idioma português (pt-BR) não é suportado pelo serviço de voz do navegador.');
        } else {
          onNotice('Não foi possível reconhecer a voz. Digite o nome ou código no campo de busca.');
        }
      };

      recognition.onend = () => {
        setIsListening(false);
      };

      recognition.start();
    } catch (err: any) {
      setIsListening(false);
      onNotice('Erro ao ativar microfone. A busca manual por texto está ativa.');
    }
  };

  const stopListening = () => {
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (_) {}
    }
    setIsListening(false);
  };

  const toggleListening = () => {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  };

  return (
    <button
      type="button"
      onClick={toggleListening}
      aria-label="Pesquisar por voz em português"
      title={isListening ? 'Parar gravação' : 'Pesquisar por voz (pt-BR)'}
      className={`relative p-3 rounded-2xl transition-all duration-200 flex items-center justify-center ${
        isListening
          ? 'bg-red-500 text-white shadow-lg shadow-red-500/30 animate-pulse ring-4 ring-red-500/20'
          : 'bg-slate-800 hover:bg-slate-700 text-amber-400 hover:text-amber-300 border border-slate-700'
      }`}
    >
      {isListening ? (
        <MicOff className="w-6 h-6 animate-spin-slow" />
      ) : (
        <Mic className="w-6 h-6" />
      )}
    </button>
  );
};
