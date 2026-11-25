import type { AlertMode, Watch, WatchInput, WatchType } from '../types/api';

export interface WatchFormState {
  title: string;
  watchType: WatchType;
  targetPrice: string;
  targetKeyword: string;
  alertMode: AlertMode;
  cooldownMinutes: number;
  checkIntervalMinutes: number;
  expiresAt: string; // yyyy-mm-dd or ''
}

export const defaultForm = (interval = 60): WatchFormState => ({
  title: '', watchType: 'ANY_CHANGE', targetPrice: '', targetKeyword: '', alertMode: 'REPEAT',
  cooldownMinutes: 60, checkIntervalMinutes: interval, expiresAt: '',
});

export function formFromWatch(w: Watch): WatchFormState {
  return {
    title: w.title, watchType: w.watchType, targetPrice: w.targetPrice != null ? String(w.targetPrice) : '',
    targetKeyword: w.targetKeyword ?? '', alertMode: w.alertMode, cooldownMinutes: w.cooldownMinutes,
    checkIntervalMinutes: w.checkIntervalMinutes, expiresAt: w.expiresAt ? w.expiresAt.slice(0, 10) : '',
  };
}

/** Client-side validation mirroring the API rules. Returns field → message. */
export function validateForm(f: WatchFormState, requireTitle: boolean): Record<string, string> {
  const e: Record<string, string> = {};
  if (requireTitle && !f.title.trim()) e.title = 'Title is required';
  if (f.title.length > 200) e.title = 'Title must be at most 200 characters';
  if (f.watchType === 'PRICE_BELOW') {
    const n = Number(f.targetPrice);
    if (!f.targetPrice.trim()) e.targetPrice = 'Enter your target price';
    else if (!Number.isFinite(n) || n <= 0) e.targetPrice = 'Target price must be a positive number';
    else if (!/^\d+(\.\d{1,2})?$/.test(f.targetPrice.trim())) e.targetPrice = 'Use at most two decimals';
  }
  if (f.watchType === 'KEYWORD_FOUND' || f.watchType === 'KEYWORD_REMOVED') {
    if (f.targetKeyword.trim().length < 2) e.targetKeyword = 'Enter a keyword of at least 2 characters';
    if (f.targetKeyword.length > 200) e.targetKeyword = 'Keyword must be at most 200 characters';
  }
  if (f.expiresAt && new Date(f.expiresAt + 'T23:59:59').getTime() <= Date.now()) e.expiresAt = 'Choose a future date';
  return e;
}

export function toInput(f: WatchFormState): WatchInput {
  return {
    title: f.title.trim() || undefined,
    watchType: f.watchType,
    targetPrice: f.watchType === 'PRICE_BELOW' ? Number(f.targetPrice) : null,
    targetKeyword: f.watchType === 'KEYWORD_FOUND' || f.watchType === 'KEYWORD_REMOVED' ? f.targetKeyword.trim() : null,
    alertMode: f.alertMode,
    cooldownMinutes: f.cooldownMinutes,
    checkIntervalMinutes: f.checkIntervalMinutes,
    expiresAt: f.expiresAt ? new Date(f.expiresAt + 'T23:59:59').toISOString() : null,
  };
}
