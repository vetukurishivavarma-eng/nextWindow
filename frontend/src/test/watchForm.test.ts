import { describe, expect, it } from 'vitest';
import { defaultForm, toInput, validateForm } from '../forms/watchForm';

describe('watch form', () => {
  it('requires a valid target price for PRICE_BELOW', () => {
    const f = { ...defaultForm(), watchType: 'PRICE_BELOW' as const };
    expect(validateForm(f, false).targetPrice).toBeDefined();
    expect(validateForm({ ...f, targetPrice: '-1' }, false).targetPrice).toBeDefined();
    expect(validateForm({ ...f, targetPrice: '9.999' }, false).targetPrice).toBeDefined();
    expect(validateForm({ ...f, targetPrice: '49.99' }, false)).toEqual({});
  });

  it('requires keywords for keyword watches and a title when editing', () => {
    expect(validateForm({ ...defaultForm(), watchType: 'KEYWORD_FOUND', targetKeyword: 'a' }, false).targetKeyword).toBeDefined();
    expect(validateForm(defaultForm(), true).title).toBeDefined();
  });

  it('only sends fields relevant to the condition', () => {
    const input = toInput({ ...defaultForm(), watchType: 'PRICE_DROP', targetPrice: '10', targetKeyword: 'x' });
    expect(input.targetPrice).toBeNull();
    expect(input.targetKeyword).toBeNull();
    const kw = toInput({ ...defaultForm(), watchType: 'KEYWORD_REMOVED', targetKeyword: '  sold out ' });
    expect(kw.targetKeyword).toBe('sold out');
  });
});
