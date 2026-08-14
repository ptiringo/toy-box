import { apiDelete, apiGet, apiPatch, apiPost, type GetToken } from "./client";

/** バックの WorldResponse（controller/world/WorldResponse.kt）に対応する世界（セーブデータ）。 */
export type World = {
  id: string;
  name: string;
};

export function listWorlds(getToken: GetToken): Promise<World[]> {
  return apiGet<World[]>("/api/worlds", getToken);
}

export function createWorld(getToken: GetToken, name: string): Promise<World> {
  return apiPost<World>("/api/worlds", getToken, { name });
}

export function renameWorld(getToken: GetToken, worldId: string, name: string): Promise<World> {
  return apiPatch<World>(`/api/worlds/${worldId}`, getToken, { name });
}

export function deleteWorld(getToken: GetToken, worldId: string): Promise<void> {
  return apiDelete(`/api/worlds/${worldId}`, getToken);
}
