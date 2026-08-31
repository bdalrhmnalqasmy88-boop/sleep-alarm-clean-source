import { Moon } from 'lucide-react';

export default function MoonIcon({ className = '' }: { className?: string }) {
  return (
    <div className={`relative ${className}`}>
      <Moon className="h-full w-full text-gold-300 drop-shadow-[0_0_25px_rgba(251,191,36,0.45)]" strokeWidth={1.5} />
    </div>
  );
}
