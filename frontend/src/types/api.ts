export type WatchType =
  | 'ANY_CHANGE'
  | 'PRICE_DROP'
  | 'PRICE_BELOW'
  | 'STOCK_AVAILABLE'
  | 'KEYWORD_FOUND'
  | 'KEYWORD_REMOVED'
  | 'STATUS_CHANGE';

export type AlertMode = 'ONE_TIME' | 'REPEAT';
export type Availability = 'IN_STOCK' | 'OUT_OF_STOCK' | 'PREORDER' | 'BACKORDER' | 'LIMITED' | 'DISCONTINUED' | 'UNKNOWN';
export type SourceStatus = 'PENDING' | 'ACTIVE' | 'DEGRADED' | 'FAILING' | 'BLOCKED' | 'DISABLED';

export interface User {
  id: number;
  email: string;
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface SourceSummary {
  id: number;
  url: string;
  host: string;
  sourceType: 'WEB_PAGE' | 'DEMO';
  status: SourceStatus;
  statusMessage: string | null;
  lastFetchedAt: string | null;
  lastSuccessAt: string | null;
  nextFetchAt: string;
  failureCount: number;
  checkIntervalMinutes: number;
}

export interface PageState {
  title: string | null;
  price: number | null;
  currency: string | null;
  priceSource: string | null;
  availability: Availability;
  capturedAt: string;
}

export interface Watch {
  id: number;
  title: string;
  watchType: WatchType;
  targetPrice: number | null;
  targetKeyword: string | null;
  active: boolean;
  alertMode: AlertMode;
  cooldownMinutes: number;
  checkIntervalMinutes: number;
  expiresAt: string | null;
  completedAt: string | null;
  createdAt: string;
  lastTriggeredAt: string | null;
  lastEvaluatedAt: string | null;
  conditionState: string;
  conditionMet: boolean;
  unreadAlerts: number;
  source: SourceSummary;
  current: PageState | null;
  previous: PageState | null;
}

export interface WatchInput {
  url?: string;
  title?: string;
  watchType: WatchType;
  targetPrice?: number | null;
  targetKeyword?: string | null;
  alertMode: AlertMode;
  cooldownMinutes: number;
  checkIntervalMinutes: number;
  expiresAt?: string | null;
}

export interface Snapshot {
  id: number;
  title: string | null;
  price: number | null;
  currency: string | null;
  priceSource: string | null;
  availability: Availability;
  createdAt: string;
}

export interface CheckRun {
  id: number;
  sourceId: number;
  sourceUrl: string;
  watchTitle: string | null;
  watchId: number | null;
  startedAt: string;
  finishedAt: string;
  outcome: 'BASELINE' | 'CHANGED' | 'UNCHANGED' | 'FAILED' | 'BLOCKED' | 'RATE_LIMITED';
  httpStatus: number | null;
  durationMs: number;
  message: string | null;
  alertsCreated: number;
}

export interface Alert {
  id: number;
  watchId: number;
  watchTitle: string | null;
  sourceUrl: string | null;
  alertType: string;
  title: string;
  message: string;
  oldValue: string | null;
  newValue: string | null;
  read: boolean;
  pushStatus: string;
  createdAt: string;
}

export interface TypeSupport {
  type: WatchType;
  recommended: boolean;
  available: boolean;
  note: string | null;
}

export interface Analysis {
  canonicalUrl: string;
  reachable: boolean;
  fromSharedSource: boolean;
  title: string | null;
  price: number | null;
  currency: string | null;
  priceSource: string | null;
  availability: Availability;
  availabilitySource: string | null;
  productDetected: boolean;
  description: string | null;
  siteName: string | null;
  textPreview: string | null;
  notes: string[];
  watchTypes: TypeSupport[];
  problem: string | null;
  analyzedAt: string;
}

export interface AttentionItem {
  watchId: number;
  watchTitle: string;
  status: string;
  message: string;
  since: string | null;
}

export interface Health {
  sources: number;
  healthy: number;
  pending: number;
  degraded: number;
  failing: number;
  blocked: number;
  lastCheckAt: string | null;
  nextCheckAt: string | null;
  schedulerRunning: boolean;
  schedulerLastTick: string | null;
  attention: AttentionItem[];
}

export interface Dashboard {
  totalWatches: number;
  activeWatches: number;
  pausedWatches: number;
  alertsLast7Days: number;
  unreadAlerts: number;
  recentAlerts: Alert[];
  recentChanges: CheckRun[];
  health: Health;
}

export interface ClientConfig {
  demoEnabled: boolean;
  pushConfigured: boolean;
  vapidPublicKey: string | null;
  intervalOptions: number[];
  defaultIntervalMinutes: number;
}

export interface Settings {
  email: string;
  pushEnabled: boolean;
  pushConfigured: boolean;
  pushDevices: number;
}

export interface PushDevice {
  id: number;
  device: string;
  endpointHost: string;
  createdAt: string;
  lastSuccessAt: string | null;
  failureCount: number;
}

export interface PushTestResult {
  devices: number;
  delivered: number;
  failed: number;
  removed: number;
  message: string;
}

export interface CheckNowResult {
  ran: boolean;
  outcome: string | null;
  alertsCreated: number;
  message: string;
  watch: Watch;
}

export interface DemoSource {
  url: string;
  slug: string;
  title: string;
  price: number | null;
  currency: string | null;
  availability: Availability;
  description: string | null;
  pendingFailures: number;
}

export type DemoAction =
  | 'PRICE_DROP'
  | 'PRICE_INCREASE'
  | 'RESTOCK'
  | 'SELL_OUT'
  | 'ADD_KEYWORD'
  | 'REMOVE_KEYWORD'
  | 'PAGE_CHANGE'
  | 'FAIL_NEXT'
  | 'RESET';

export interface SimulateResult {
  source: DemoSource;
  checked: boolean;
  outcome: string | null;
  alertsCreated: number;
  message: string;
}
