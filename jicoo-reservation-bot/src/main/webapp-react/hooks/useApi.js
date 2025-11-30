import { useMemo } from "react";

export function useApi(baseUrl) {
  return useMemo(() => {
    const get = async (endpoint) => {
      const response = await fetch(`${baseUrl}${endpoint}`, {
        headers: {
          "Content-Type": "application/json",
        },
      });
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(
          `HTTP error! status: ${response.status}, message: ${errorText}`
        );
      }
      return await response.json();
    };

    const post = async (endpoint, data) => {
      const response = await fetch(`${baseUrl}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(
          `HTTP error! status: ${response.status}, message: ${errorText}`
        );
      }
      return await response.json();
    };

    const put = async (endpoint, data) => {
      const response = await fetch(`${baseUrl}${endpoint}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(
          `HTTP error! status: ${response.status}, message: ${errorText}`
        );
      }
      return await response.json();
    };

    const del = async (endpoint) => {
      const response = await fetch(`${baseUrl}${endpoint}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
      });
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(
          `HTTP error! status: ${response.status}, message: ${errorText}`
        );
      }
      return await response.json();
    };

    return { get, post, put, delete: del };
  }, [baseUrl]);
}
