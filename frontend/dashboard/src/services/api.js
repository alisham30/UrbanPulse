import axios from 'axios';

const API_BASE_URL = '/api';
const WS_BASE_URL = '/ws';

/**
 * REST API client for fetching dashboard data
 */
export const apiClient = {
  getDashboard: async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/dashboard`);
      return response.data;
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
      throw error;
    }
  },

  getLatestMetrics: async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/latest`);
      return response.data;
    } catch (error) {
      console.error('Error fetching latest metrics:', error);
      throw error;
    }
  },

  getCityMetrics: async (city) => {
    try {
      const response = await axios.get(`${API_BASE_URL}/latest?city=${encodeURIComponent(city)}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching metrics for ${city}:`, error);
      throw error;
    }
  },

  getAlerts: async (limit = 20, city = null) => {
    try {
      const cityParam = city ? `&city=${encodeURIComponent(city)}` : '';
      const response = await axios.get(`${API_BASE_URL}/alerts?limit=${limit}${cityParam}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching alerts:', error);
      throw error;
    }
  },

  getCities: async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/cities`);
      return response.data;
    } catch (error) {
      console.error('Error fetching cities:', error);
      throw error;
    }
  },

  getAqiHistory: async (city) => {
    try {
      const response = await axios.get(`${API_BASE_URL}/history?city=${encodeURIComponent(city)}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching AQI history for ${city}:`, error);
      return { history: [] };
    }
  },

  getHealth: async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/health`);
      return response.data;
    } catch (error) {
      console.error('Error checking service health:', error);
      throw error;
    }
  }
};

/**
 * WebSocket client for real-time updates
 */
class WebSocketClient {
  constructor(url) {
    this.url = url;
    this.stompClient = null;
    this.messageCallback = null;
    this.isConnected = false;
  }

  connect(onMessageReceived) {
    return new Promise((resolve, reject) => {
      this.messageCallback = onMessageReceived;
      
      const socket = new SockJS(this.url);
      this.stompClient = Stomp.over(socket);

      this.stompClient.connect(
        {},
        (frame) => {
          console.log('WebSocket connected:', frame.headers.server);
          this.isConnected = true;

          // Subscribe to city updates
          this.stompClient.subscribe('/topic/city-updates', (message) => {
            try {
              const update = JSON.parse(message.body);
              this.messageCallback(update);
            } catch (error) {
              console.error('Error parsing WebSocket message:', error);
            }
          });

          resolve();
        },
        (error) => {
          console.error('WebSocket connection error:', error);
          this.isConnected = false;
          reject(error);
        }
      );
    });
  }

  disconnect() {
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.disconnect(() => {
        console.log('WebSocket disconnected');
        this.isConnected = false;
      });
    }
  }

  getIsConnected() {
    return this.isConnected;
  }
}

export const wsClient = new WebSocketClient(WS_BASE_URL);
