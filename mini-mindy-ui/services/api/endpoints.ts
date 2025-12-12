
const BASE_URL = "";

export const ENDPOINTS = {
  API: BASE_URL,

  CHAT: {
    BASE: `${BASE_URL}/chat`,
    SEND: `${BASE_URL}/chat`,
  },

  DASHBOARD: {
    BASE: `${BASE_URL}/dashboard`,
    PIPELINE_METRICS: `${BASE_URL}/dashboard/pipeline-metrics`,
    DAG_RUNS: `${BASE_URL}/dashboard/dag-runs`,
    DATA_LAKE_STATS: `${BASE_URL}/dashboard/data-lake-stats`,
    KAFKA_LAG: `${BASE_URL}/dashboard/kafka-lag`,
  },

  AUTH: {
    LOGIN: `${BASE_URL}/auth/login`,
    REGISTER: `${BASE_URL}/auth/register`,
    LOGOUT: `${BASE_URL}/auth/logout`,
  },
  GOOGLE: {
    BASE: `${BASE_URL}/google`,
    COMPLETE_REGISTRATION: `${BASE_URL}/auth/google/complete-registration`,
  },
  EMAILS: {
    RECENT: `${BASE_URL}/emails/recent`,
  },
};

